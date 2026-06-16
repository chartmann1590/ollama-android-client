/* =========================================================
   Ollama AI Chat — Web Client
   Firebase Realtime Database + Auth
   ========================================================= */

// Firebase config is injected by GitHub Actions deploy step.
// The placeholder below is replaced with the actual JSON value.
const firebaseConfig = __FIREBASE_CONFIG__;

let auth = null, db = null;
try {
    firebase.initializeApp(firebaseConfig);
    if (firebaseConfig.appCheckSiteKey && firebase.appCheck) {
        firebase.appCheck().activate(firebaseConfig.appCheckSiteKey, true);
    }
    auth = firebase.auth();
    db   = firebase.database();
} catch (e) {
    console.warn('Firebase init failed (config missing or invalid):', e.message);
}

// ---- State ----
let uid              = null;
let userRef          = null;
let activeThreadId   = null;   // current thread's syncId UUID
let messagesListener = null;   // detach handle
let pendingImages    = [];     // { dataUrl, base64 } objects
let knownModels      = new Set();  // models from phone's availableModels path
let modelsListener   = null;   // real-time listener handle for availableModels
// Threads created locally before phone confirms — map of syncId → { title, model, updatedAt }
const pendingThreads = new Map();

// Phone presence (real-time listener)
let phoneDevicesRef         = null;
let phoneOnlineCallback     = null;
let phoneStatusRevalidateTimer = null;
let lastDeviceUpdatedAt     = 0;
let isPhoneOnline           = false;

// Offline message queue
let offlineQueue = [];  // { requestId, request, threadSyncId, isNewThread, threadTitle }

// ---- Web Sync Premium / daily usage ----
const WEB_DAILY_LIMIT  = 3;
let isWebSyncPremium   = false;
let dailyMsgCount      = 0;
let dailyMsgDate       = '';
let subscriptionRef    = null;
let dailyUsageRef      = null;

// ---- Auth state ----
if (!auth) {
    console.warn('Auth unavailable — Firebase config not set.');
}
(auth || { onAuthStateChanged: () => {} }).onAuthStateChanged(user => {
    if (user) {
        uid     = user.uid;
        userRef = db.ref('users/' + uid);
        showChatShell(user);
    } else {
        uid           = null;
        userRef       = null;
        activeThreadId = null;
        clearMessagesListener();
        stopModelsListener();
        stopPhoneStatusMonitor();
        stopSubscriptionListener();
        stopDailyUsageListener();
        showAuthWall();
    }
});

// ---- Auth wall ----
function showAuthWall() {
    document.getElementById('auth-section').style.display = 'flex';
    document.getElementById('chat-shell').classList.remove('visible');
    document.getElementById('auth-error').classList.remove('visible');
}

function showChatShell(user) {
    document.getElementById('auth-section').style.display = 'none';
    const shell = document.getElementById('chat-shell');
    shell.classList.add('visible');

    // Set user info
    const email = user.email || '';
    document.getElementById('user-email').textContent = email;
    document.getElementById('user-avatar').textContent = email.charAt(0).toUpperCase() || '?';

    loadThreadList();
    startModelsListener();
    startPhoneStatusMonitor();
    startSubscriptionListener();
    startDailyUsageListener();
    showEmptyChatState();
}

// Google sign-in (popup — works with subpath on GitHub Pages)
document.getElementById('btn-google').addEventListener('click', () => {
    clearAuthError();
    if (!auth) { showAuthError('Firebase is not configured on this deployment.'); return; }
    const provider = new firebase.auth.GoogleAuthProvider();
    auth.signInWithPopup(provider).catch(err => showAuthError(err.message));
});

// Email sign-in
document.getElementById('btn-signin').addEventListener('click', () => {
    clearAuthError();
    const email = document.getElementById('auth-email').value.trim();
    const pass  = document.getElementById('auth-password').value;
    if (!email || !pass) { showAuthError('Please enter your email and password.'); return; }
    if (!auth) { showAuthError('Firebase is not configured on this deployment.'); return; }
    auth.signInWithEmailAndPassword(email, pass).catch(err => showAuthError(friendlyAuthError(err)));
});

// Create account
document.getElementById('btn-create').addEventListener('click', () => {
    clearAuthError();
    const email = document.getElementById('auth-email').value.trim();
    const pass  = document.getElementById('auth-password').value;
    if (!email || !pass) { showAuthError('Please enter an email and password.'); return; }
    if (pass.length < 6) { showAuthError('Password must be at least 6 characters.'); return; }
    if (!auth) { showAuthError('Firebase is not configured on this deployment.'); return; }
    auth.createUserWithEmailAndPassword(email, pass).catch(err => showAuthError(friendlyAuthError(err)));
});

// Forgot password
document.getElementById('btn-forgot').addEventListener('click', e => {
    e.preventDefault();
    const email = document.getElementById('auth-email').value.trim();
    if (!email) { showAuthError('Enter your email address above first.'); return; }
    if (!auth) { showAuthError('Firebase is not configured on this deployment.'); return; }
    auth.sendPasswordResetEmail(email)
        .then(() => showAuthError('Password reset email sent — check your inbox.', false))
        .catch(err => showAuthError(err.message));
});

// Sign out
document.getElementById('btn-signout').addEventListener('click', () => {
    if (auth) auth.signOut();
});

function showAuthError(msg, isError = true) {
    const el = document.getElementById('auth-error');
    el.textContent = msg;
    el.classList.add('visible');
    if (!isError) el.style.color = '#16a34a';
    else          el.style.color = '';
}

function clearAuthError() {
    document.getElementById('auth-error').classList.remove('visible');
}

function friendlyAuthError(err) {
    const code = err.code || '';
    if (code === 'auth/user-not-found' || code === 'auth/wrong-password' || code === 'auth/invalid-credential')
        return 'Invalid email or password.';
    if (code === 'auth/email-already-in-use')
        return 'An account with that email already exists.';
    if (code === 'auth/weak-password')
        return 'Password is too weak. Use at least 6 characters.';
    if (code === 'auth/invalid-email')
        return 'Please enter a valid email address.';
    if (code === 'auth/too-many-requests')
        return 'Too many attempts. Please try again later.';
    return err.message;
}

// ---- Thread list ----
function loadThreadList() {
    const list = document.getElementById('thread-list');
    list.innerHTML = threadSkeletons();

    userRef.child('threads').orderByChild('updatedAt').on('value', snap => {
        const threads = [];
        snap.forEach(child => {
            const t = child.val();
            if (!t || t.deleted) return;
            threads.push({ syncId: child.key, ...t });
        });
        threads.sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
        renderThreadList(threads);
    });
}

function prependPendingThread(syncId, title, model) {
    const list = document.getElementById('thread-list');
    // Remove existing placeholder for this syncId if any
    list.querySelector(`[data-id="${CSS.escape(syncId)}"]`)?.remove();
    const div = document.createElement('div');
    div.className = 'thread-item' + (syncId === activeThreadId ? ' active' : '');
    div.dataset.id = syncId;
    div.onclick = () => openThread(syncId, title, model);
    div.innerHTML = `
        <div class="thread-title">${escHtml(title)}</div>
        <div class="thread-meta">
            ${model ? `<span class="thread-model">${escHtml(model)}</span>` : ''}
            <span class="thread-time">just now</span>
        </div>`;
    list.insertBefore(div, list.firstChild);
}

function renderThreadList(threads) {
    const list = document.getElementById('thread-list');

    // Remove pending threads that have now been confirmed by Firebase
    threads.forEach(t => pendingThreads.delete(t.syncId));

    // Fallback: if the phone hasn't published availableModels yet, use models seen in threads
    if (knownModels.size === 0) {
        threads.forEach(t => { if (t.model) knownModels.add(t.model); });
        if (knownModels.size > 0) updateModelSelector();
    }

    // Merge: pending (not yet confirmed) + Firebase threads
    const pendingNotYetConfirmed = [...pendingThreads.values()];
    const allThreads = [
        ...pendingNotYetConfirmed,
        ...threads.filter(t => !pendingThreads.has(t.syncId))
    ].sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));

    if (!allThreads.length) {
        list.innerHTML = '<div style="padding:1rem;font-size:0.85rem;color:var(--text-light);text-align:center;">No conversations yet.<br>Start a new chat below.</div>';
        return;
    }

    list.replaceChildren(...allThreads.map(t => createThreadItem(t)));

    // Restore model badge for the active thread (may have no model in Firebase but one selected)
    if (activeThreadId) {
        const selectedModel = document.getElementById('model-select').value;
        if (selectedModel) updateActiveThreadModelBadge(selectedModel);
    }
}

function createThreadItem(t) {
    const syncId = String(t.syncId || '');
    const title = t.title || 'Chat';
    const model = t.model || '';
    const div = document.createElement('div');
    div.className = 'thread-item' + (syncId === activeThreadId ? ' active' : '');
    div.dataset.id = syncId;
    div.addEventListener('click', () => openThread(syncId, title, model));

    const titleEl = document.createElement('div');
    titleEl.className = 'thread-title';
    titleEl.textContent = title;

    const meta = document.createElement('div');
    meta.className = 'thread-meta';
    if (model) {
        const modelEl = document.createElement('span');
        modelEl.className = 'thread-model';
        modelEl.textContent = model;
        meta.appendChild(modelEl);
    }
    const timeEl = document.createElement('span');
    timeEl.className = 'thread-time';
    timeEl.textContent = relTime(t.updatedAt);
    meta.appendChild(timeEl);

    div.append(titleEl, meta);
    return div;
}

function updateActiveThreadModelBadge(model) {
    const m = model !== undefined ? model : document.getElementById('model-select').value;
    if (!activeThreadId || !m) return;
    const el = document.querySelector(`.thread-item[data-id="${CSS.escape(activeThreadId)}"]`);
    if (!el) return;
    let badge = el.querySelector('.thread-model');
    if (!badge) {
        badge = document.createElement('span');
        badge.className = 'thread-model';
        const meta = el.querySelector('.thread-meta');
        if (meta) meta.insertBefore(badge, meta.firstChild);
    }
    badge.textContent = m;
}

function updateModelSelector() {
    const sel = document.getElementById('model-select');
    const prev = sel.value;
    const placeholder = document.createElement('option');
    placeholder.value = '';
    placeholder.textContent = '— select a model —';
    const options = Array.from(knownModels).sort().map(m => {
        const option = document.createElement('option');
        option.value = m;
        option.textContent = m;
        return option;
    });
    sel.replaceChildren(placeholder, ...options);
    // Restore previous selection if still valid
    if (prev && knownModels.has(prev)) sel.value = prev;
    // Auto-select if only one option
    else if (knownModels.size === 1) sel.value = [...knownModels][0];
}

function threadSkeletons() {
    return [80, 65, 70].map(w => `
        <div class="thread-skeleton">
          <div class="skeleton-line" style="width:${w}%"></div>
          <div class="skeleton-line short"></div>
        </div>`).join('');
}

// ---- Open / switch thread ----
function openThread(threadSyncId, title, model) {
    if (activeThreadId === threadSyncId) return;
    activeThreadId = threadSyncId;

    // Update active highlight
    document.querySelectorAll('.thread-item').forEach(el => {
        el.classList.toggle('active', el.dataset.id === threadSyncId);
    });

    // Pre-select this thread's model in the selector
    if (model) {
        knownModels.add(model);
        updateModelSelector();
        document.getElementById('model-select').value = model;
    }

    // Effective model — stored model, or whatever is already selected in the dropdown
    const effectiveModel = model || document.getElementById('model-select').value ||
        (knownModels.size > 0 ? [...knownModels][0] : '');
    if (!model && effectiveModel) {
        document.getElementById('model-select').value = effectiveModel;
    }

    // Update header
    document.getElementById('chat-header-title').textContent = title || 'Chat';
    const modelEl = document.getElementById('chat-header-model');
    modelEl.textContent = effectiveModel || '';
    modelEl.style.display = effectiveModel ? '' : 'none';
    document.getElementById('chat-header').style.display = '';
    document.getElementById('chat-header-empty').style.display = 'none';

    // Update the sidebar badge for this thread to match the effective model
    updateActiveThreadModelBadge(effectiveModel);

    // Enable input
    setInputEnabled(true);

    // Close mobile drawer
    closeMobileSidebar();

    // Load messages
    loadMessages(threadSyncId);
}

function loadMessages(threadSyncId) {
    clearMessagesListener();
    const list = document.getElementById('message-list');
    list.innerHTML = '';

    const ref = userRef.child('messages/' + threadSyncId).orderByChild('timestamp');
    messagesListener = { ref, fn: null };

    ref.on('value', snap => {
        const msgs = [];
        snap.forEach(child => {
            const m = child.val();
            if (!m || m.deleted) return;
            msgs.push(m);
        });
        renderMessages(msgs);
        scrollToBottom();
    });

    messagesListener.fn = ref;
}

function clearMessagesListener() {
    if (messagesListener && messagesListener.ref) {
        messagesListener.ref.off('value');
        messagesListener = null;
    }
}

function renderMessages(msgs) {
    const list = document.getElementById('message-list');
    if (!msgs.length) {
        list.innerHTML = `<div class="message-empty">
            <div class="message-empty-icon">💬</div>
            <div class="message-empty-text">No messages yet. Send one below!</div>
        </div>`;
        return;
    }

    list.innerHTML = msgs.map(m => {
        const isUser   = m.role === 'user';
        const isAssist = m.role === 'assistant';

        let imagesHtml = '';
        const safeImages = Array.isArray(m.images) ? m.images.filter(isSafeBase64Image) : [];
        if (safeImages.length) {
            imagesHtml = '<div class="message-images">' +
                safeImages.map(b64 => `<img class="message-image" src="data:image/jpeg;base64,${b64}" alt="attached image">`).join('') +
                '</div>';
        }

        let thinkingHtml = '';
        if (m.thinking) {
            const id = 'think-' + Math.random().toString(36).slice(2);
            thinkingHtml = `<div class="thinking-block">
                <button class="thinking-toggle" onclick="toggleThinking('${id}', this)">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="6 9 12 15 18 9"/></svg>
                    Reasoning
                </button>
                <div class="thinking-content" id="${id}">${escHtml(m.thinking)}</div>
            </div>`;
        }

        const contentHtml = isAssist
            ? renderSafeMarkdown(m.content || '')
            : escHtml(m.content || '');

        return `<div class="message ${m.role}">
            <div class="message-role">${m.role === 'user' ? 'You' : 'Assistant'}</div>
            ${imagesHtml}
            ${thinkingHtml}
            <div class="message-bubble">${contentHtml}</div>
        </div>`;
    }).join('');
}

function toggleThinking(id, btn) {
    const el = document.getElementById(id);
    const open = el.classList.toggle('open');
    btn.classList.toggle('open', open);
}

function showEmptyChatState() {
    document.getElementById('chat-header').style.display = 'none';
    document.getElementById('chat-header-empty').style.display = '';
    document.getElementById('message-list').innerHTML = `<div class="message-empty">
        <div class="message-empty-icon">🤖</div>
        <div class="message-empty-text">Select a conversation from the sidebar<br>or start a new chat.</div>
    </div>`;
    setInputEnabled(false);
}

// ---- New chat ----
document.getElementById('btn-new-chat').addEventListener('click', () => {
    activeThreadId = null;
    document.querySelectorAll('.thread-item').forEach(el => el.classList.remove('active'));
    document.getElementById('chat-header-title').textContent = 'New Chat';
    const modelEl = document.getElementById('chat-header-model');
    modelEl.textContent = '';
    modelEl.style.display = 'none';
    document.getElementById('chat-header').style.display = '';
    document.getElementById('chat-header-empty').style.display = 'none';
    document.getElementById('message-list').innerHTML = `<div class="message-empty">
        <div class="message-empty-icon">✨</div>
        <div class="message-empty-text">New conversation — type your first message below.</div>
    </div>`;
    clearMessagesListener();
    setInputEnabled(true);
    closeMobileSidebar();
    document.getElementById('msg-input').focus();
});

// ---- Send message ----
document.getElementById('send-btn').addEventListener('click', sendMessage);
document.getElementById('msg-input').addEventListener('keydown', e => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); }
});

// When model changes, update the active thread's sidebar badge + header
document.getElementById('model-select').addEventListener('change', () => {
    const model = document.getElementById('model-select').value;
    updateActiveThreadModelBadge(model);
    if (activeThreadId && model) {
        const modelEl = document.getElementById('chat-header-model');
        modelEl.textContent = model;
        modelEl.style.display = '';
    }
});

// Auto-resize textarea
document.getElementById('msg-input').addEventListener('input', function() {
    this.style.height = 'auto';
    this.style.height = Math.min(this.scrollHeight, 140) + 'px';
    document.getElementById('send-btn').disabled = !this.value.trim() && !pendingImages.length;
});

function setInputEnabled(enabled) {
    const input    = document.getElementById('msg-input');
    const sendBtn  = document.getElementById('send-btn');
    const attach   = document.getElementById('btn-attach');
    const modelSel = document.getElementById('model-select');
    const modelBar = document.getElementById('model-bar');
    input.disabled    = !enabled;
    attach.disabled   = !enabled;
    modelSel.disabled = !enabled;
    sendBtn.disabled  = !enabled || (!input.value.trim() && !pendingImages.length);
    if (enabled) {
        modelBar.classList.add('visible');
        input.focus();
    } else {
        modelBar.classList.remove('visible');
    }
}

async function sendMessage() {
    const input    = document.getElementById('msg-input');
    const content  = input.value.trim();
    const model    = document.getElementById('model-select').value;
    if (!content && !pendingImages.length) return;
    if (!uid || !userRef) return;
    if (!model) {
        setStatus('Select a model before sending.', true);
        document.getElementById('model-select').focus();
        return;
    }

    // Enforce daily web message limit for free users
    if (!isWebSyncPremium) {
        const today = getUtcDateString();
        if (dailyMsgDate !== today) {
            // New day — reset locally and sync to Firebase before the webRequest write
            // so the security rule sees count=0. The listener may not have fired yet.
            dailyMsgDate  = today;
            dailyMsgCount = 0;
            if (userRef) {
                try { await userRef.child('webSyncDailyUsage').set({ date: today, count: 0 }); }
                catch (e) { console.warn('Failed to reset daily usage:', e); }
            }
            updateWebSyncUI();
        }
        if (dailyMsgCount >= WEB_DAILY_LIMIT) {
            showPremiumModal();
            return;
        }
    }

    // If no active thread, create one now so messages show immediately
    const isNewThread  = !activeThreadId;
    const threadSyncId = activeThreadId || crypto.randomUUID();
    const threadTitle  = isNewThread ? content.slice(0, 60) : null;

    // Grab images then clear preview
    const images = pendingImages.map(p => p.base64);
    clearImagePreviews();

    // Clear input
    input.value = '';
    input.style.height = 'auto';
    document.getElementById('send-btn').disabled = true;

    const requestId = crypto.randomUUID();
    const request = {
        threadSyncId,
        content,
        model,
        status: 'pending',
        createdAt: Date.now(),
        ...(threadTitle ? { threadTitle } : {}),
        ...(images.length ? { images } : {})
    };

    // Activate new thread UI immediately (even if phone is offline)
    if (isNewThread) {
        activeThreadId = threadSyncId;
        pendingThreads.set(threadSyncId, { syncId: threadSyncId, title: threadTitle || 'New Chat', model, updatedAt: Date.now() });
        prependPendingThread(threadSyncId, threadTitle || 'New Chat', model);
        document.getElementById('chat-header-title').textContent = threadTitle || 'New Chat';
        const modelEl = document.getElementById('chat-header-model');
        modelEl.textContent = model;
        modelEl.style.display = '';
        document.getElementById('chat-header').style.display = '';
        document.getElementById('chat-header-empty').style.display = 'none';
        loadMessages(threadSyncId);
    }

    if (!isPhoneOnline) {
        // Queue locally — will be dispatched when phone comes online
        offlineQueue.push({ requestId, request });
        updateQueuedStatus();
        setInputEnabled(true);
        return;
    }

    // Phone is online — send immediately
    await dispatchRequest(requestId, request);
}

async function dispatchRequest(requestId, request) {
    setInputEnabled(false);
    setStatus('Sending…');
    try {
        request.createdAt = Date.now();
        await userRef.child('webRequests/' + requestId).set(request);
    } catch (err) {
        // Firebase rule rejection shows up here for free users over the limit
        if (!isWebSyncPremium && (err.code === 'PERMISSION_DENIED' || (err.message && err.message.includes('PERMISSION_DENIED')))) {
            setStatus('Daily limit reached. Upgrade for unlimited web messages.', true);
            dailyMsgCount = WEB_DAILY_LIMIT;
            updateWebSyncUI();
        } else {
            setStatus('Error sending: ' + err.message, true);
        }
        setInputEnabled(true);
        return;
    }
    // Increment daily counter for free users after a successful write
    if (!isWebSyncPremium) {
        incrementDailyUsage();
    }
    watchRequest(requestId);
}

async function drainOfflineQueue() {
    if (!offlineQueue.length || !userRef) return;
    const queue = [...offlineQueue];
    offlineQueue = [];
    clearStatus();
    for (const item of queue) {
        await dispatchRequest(item.requestId, item.request);
        // Wait for this request to complete before dispatching next
        await new Promise(resolve => {
            const ref = userRef.child('webRequests/' + item.requestId);
            const handler = ref.on('value', snap => {
                const status = snap.val() && snap.val().status;
                if (status === 'complete' || status === 'error' || !status) {
                    ref.off('value', handler);
                    resolve();
                }
            });
        });
    }
}

function updateQueuedStatus() {
    const n = offlineQueue.length;
    if (n === 0) { clearStatus(); return; }
    const bar = document.getElementById('status-bar');
    bar.innerHTML = `<div class="status-spinner"></div><span>${n} message${n > 1 ? 's' : ''} queued — waiting for phone to come online…</span>`;
    bar.className = '';
}

function watchRequest(requestId) {
    const ref = userRef.child('webRequests/' + requestId);
    const handler = ref.on('value', snap => {
        const val    = snap.val();
        const status = val && val.status;

        if (status === 'pending')  setStatus('Sending…');
        if (status === 'running')  setStatus('Phone is generating response…');
        if (status === 'complete') {
            clearStatus();
            setInputEnabled(true);
            ref.off('value', handler);
        }
        if (status === 'error') {
            setStatus('Error: ' + (val.error || val.errorMessage || 'Unknown error'), true);
            setInputEnabled(true);
            ref.off('value', handler);
        }
        if (!status) {
            // Request was cleaned up
            clearStatus();
            setInputEnabled(true);
            ref.off('value', handler);
        }
    });
}

// ---- Status bar ----
function setStatus(text, isError) {
    const bar = document.getElementById('status-bar');
    bar.innerHTML = isError
        ? `<span>${escHtml(text)}</span>`
        : `<div class="status-spinner"></div><span>${escHtml(text)}</span>`;
    bar.className = isError ? 'error' : '';
}

function clearStatus() {
    const bar = document.getElementById('status-bar');
    bar.innerHTML = '';
    bar.className = '';
}

// ---- Image attach ----
document.getElementById('btn-attach').addEventListener('click', () => {
    document.getElementById('image-input').click();
});

document.getElementById('image-input').addEventListener('change', e => {
    const files = Array.from(e.target.files || []);
    files.forEach(file => {
        if (!file.type.startsWith('image/')) return;
        const reader = new FileReader();
        reader.onload = ev => {
            const dataUrl = ev.target.result;
            // Strip the data URI prefix to get raw base64
            const base64  = dataUrl.split(',')[1];
            if (!isSafeBase64Image(base64)) {
                setStatus('Image could not be attached safely.', true);
                return;
            }
            pendingImages.push({ dataUrl, base64 });
            renderImagePreviews();
            document.getElementById('send-btn').disabled = false;
        };
        reader.readAsDataURL(file);
    });
    e.target.value = '';
});

function renderImagePreviews() {
    const strip = document.getElementById('image-preview');
    strip.innerHTML = pendingImages.map((p, i) => `
        <div class="preview-thumb">
            <img src="${p.dataUrl}" alt="preview">
            <button class="preview-remove" onclick="removeImage(${i})" aria-label="Remove image">&times;</button>
        </div>
    `).join('');
}

function removeImage(idx) {
    pendingImages.splice(idx, 1);
    renderImagePreviews();
    if (!pendingImages.length && !document.getElementById('msg-input').value.trim()) {
        document.getElementById('send-btn').disabled = true;
    }
}

function clearImagePreviews() {
    pendingImages = [];
    document.getElementById('image-preview').innerHTML = '';
}

// ---- Available models (real-time from phone) ----
function startModelsListener() {
    stopModelsListener();
    if (!userRef) return;
    const ref = userRef.child('availableModels');
    modelsListener = { ref };
    ref.on('value', snap => {
        const models = new Set();
        snap.forEach(child => {
            const d = child.val();
            if (d && d.name) models.add(d.name);
        });
        if (models.size > 0) {
            knownModels = models;
            updateModelSelector();
        }
    });
}

function stopModelsListener() {
    if (modelsListener && modelsListener.ref) {
        modelsListener.ref.off('value');
        modelsListener = null;
    }
    knownModels = new Set();
}

// ---- Phone status (real-time) ----
function startPhoneStatusMonitor() {
    stopPhoneStatusMonitor();
    if (!userRef) return;

    phoneDevicesRef = userRef.child('devices');
    phoneOnlineCallback = phoneDevicesRef.on('value', snap => {
        let maxUpdatedAt = 0;
        snap.forEach(child => {
            const d = child.val();
            // Skip devices that have explicitly marked themselves offline
            if (d && d.online !== false && d.updatedAt > maxUpdatedAt) maxUpdatedAt = d.updatedAt;
        });
        lastDeviceUpdatedAt = maxUpdatedAt;
        evaluatePhoneOnline();
    });

    // Re-check every 60s — Firebase won't re-fire if the stored value simply ages past the threshold
    phoneStatusRevalidateTimer = setInterval(evaluatePhoneOnline, 60000);
}

function stopPhoneStatusMonitor() {
    if (phoneDevicesRef && phoneOnlineCallback) {
        phoneDevicesRef.off('value', phoneOnlineCallback);
        phoneDevicesRef = null;
        phoneOnlineCallback = null;
    }
    if (phoneStatusRevalidateTimer) {
        clearInterval(phoneStatusRevalidateTimer);
        phoneStatusRevalidateTimer = null;
    }
    lastDeviceUpdatedAt = 0;
    isPhoneOnline = false;
    offlineQueue = [];
    setPhoneStatus(false);
}

function evaluatePhoneOnline() {
    const twoMinAgo = Date.now() - 2 * 60 * 1000;
    const nowOnline  = lastDeviceUpdatedAt > twoMinAgo;
    const wasOnline  = isPhoneOnline;
    isPhoneOnline    = nowOnline;
    setPhoneStatus(nowOnline);

    if (!wasOnline && nowOnline && offlineQueue.length > 0) {
        drainOfflineQueue();
    }
    if (!nowOnline && offlineQueue.length > 0) {
        updateQueuedStatus();
    }
}

function setPhoneStatus(online) {
    const dot   = document.getElementById('phone-dot');
    const label = document.getElementById('phone-label');
    if (!dot || !label) return;
    dot.className   = 'status-dot ' + (online ? 'online' : 'offline');
    label.textContent = online ? 'Phone online' : 'Phone offline';
}

// ---- Mobile sidebar ----
document.getElementById('btn-sidebar-toggle').addEventListener('click', () => {
    document.getElementById('thread-sidebar').classList.add('open');
    document.getElementById('sidebar-overlay').classList.add('open');
});

document.getElementById('sidebar-overlay').addEventListener('click', closeMobileSidebar);

function closeMobileSidebar() {
    document.getElementById('thread-sidebar').classList.remove('open');
    document.getElementById('sidebar-overlay').classList.remove('open');
}

// ---- Web Sync Premium — subscription & daily usage ----
function getUtcDateString() {
    const d = new Date();
    const yyyy = d.getUTCFullYear();
    const mm   = String(d.getUTCMonth() + 1).padStart(2, '0');
    const dd   = String(d.getUTCDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
}

function startSubscriptionListener() {
    stopSubscriptionListener();
    if (!userRef) return;
    subscriptionRef = userRef.child('subscription/webSyncPremium');
    subscriptionRef.on('value', snap => {
        isWebSyncPremium = snap.val() === true;
        updateWebSyncUI();
    });
}

function stopSubscriptionListener() {
    if (subscriptionRef) { subscriptionRef.off('value'); subscriptionRef = null; }
    isWebSyncPremium = false;
}

function startDailyUsageListener() {
    stopDailyUsageListener();
    if (!userRef) return;
    dailyUsageRef = userRef.child('webSyncDailyUsage');
    dailyUsageRef.on('value', snap => {
        const val  = snap.val();
        const today = getUtcDateString();
        if (val && val.date === today) {
            dailyMsgDate  = val.date;
            dailyMsgCount = val.count || 0;
        } else {
            // New day (or no data) — reset locally and write to Firebase so the
            // security rule sees count=0 before the next webRequest write.
            dailyMsgDate  = today;
            dailyMsgCount = 0;
            if (userRef) {
                userRef.child('webSyncDailyUsage').set({ date: today, count: 0 });
            }
        }
        updateWebSyncUI();
    });
}

function stopDailyUsageListener() {
    if (dailyUsageRef) { dailyUsageRef.off('value'); dailyUsageRef = null; }
    dailyMsgCount = 0;
    dailyMsgDate  = '';
}

function checkAndResetDailyUsageIfNewDay() {
    const today = getUtcDateString();
    if (dailyMsgDate !== today) {
        dailyMsgDate  = today;
        dailyMsgCount = 0;
    }
}

function incrementDailyUsage() {
    checkAndResetDailyUsageIfNewDay();
    dailyMsgCount++;
    if (userRef) {
        userRef.child('webSyncDailyUsage').set({ date: dailyMsgDate, count: dailyMsgCount });
    }
    updateWebSyncUI();
}

function updateWebSyncUI() {
    const banner   = document.getElementById('websync-banner');
    if (!banner) return;

    if (isWebSyncPremium) {
        banner.style.display = 'none';
        banner.innerHTML = '';
        // Re-enable send if it was blocked by limit
        const input = document.getElementById('msg-input');
        if (input && !input.disabled) {
            document.getElementById('send-btn').disabled = !input.value.trim() && !pendingImages.length;
        }
        return;
    }

    const today     = getUtcDateString();
    const count     = (dailyMsgDate === today) ? dailyMsgCount : 0;
    const remaining = Math.max(0, WEB_DAILY_LIMIT - count);
    const resetTime = getNextUtcMidnight();

    if (remaining === 0) {
        banner.style.display = '';
        banner.className = 'websync-banner limit-reached';
        banner.innerHTML = `
            <span class="websync-icon">⚠️</span>
            <span class="websync-text">Daily limit reached — <strong>${WEB_DAILY_LIMIT}/${WEB_DAILY_LIMIT}</strong> messages used. Resets at midnight UTC (${resetTime}).
            <button class="websync-upgrade-btn" onclick="showPremiumModal()">Get Unlimited</button></span>`;
        // Disable send button visually
        document.getElementById('send-btn').disabled = true;
    } else {
        banner.style.display = '';
        banner.className = 'websync-banner free-tier';
        banner.innerHTML = `
            <span class="websync-icon">💬</span>
            <span class="websync-text"><strong>${count}/${WEB_DAILY_LIMIT}</strong> web messages today · ${remaining} remaining · resets at midnight UTC
            <button class="websync-upgrade-btn" onclick="showPremiumModal()">Upgrade</button></span>`;
    }
}

function getNextUtcMidnight() {
    const now  = new Date();
    const next = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1));
    const diff = next - now;
    const h    = Math.floor(diff / 3600000);
    const m    = Math.floor((diff % 3600000) / 60000);
    return `in ${h}h ${m}m`;
}

function showPremiumModal() {
    const overlay = document.getElementById('premium-modal-overlay');
    if (overlay) overlay.style.display = 'flex';
}

function hidePremiumModal() {
    const overlay = document.getElementById('premium-modal-overlay');
    if (overlay) overlay.style.display = 'none';
}

document.getElementById('btn-premium-modal-close')?.addEventListener('click', hidePremiumModal);
document.getElementById('premium-modal-overlay')?.addEventListener('click', e => {
    if (e.target === document.getElementById('premium-modal-overlay')) hidePremiumModal();
});

// ---- Test hooks (used by Playwright E2E only) ----
window.__testSetPhoneOnline = function(online) {
    const wasOnline = isPhoneOnline;
    isPhoneOnline   = online;
    setPhoneStatus(online);
    if (!wasOnline && online && offlineQueue.length > 0) drainOfflineQueue();
    if (!online && offlineQueue.length > 0) updateQueuedStatus();
};

// ---- Helpers ----
function scrollToBottom() {
    const list = document.getElementById('message-list');
    list.scrollTop = list.scrollHeight;
}

function relTime(ts) {
    if (!ts) return '';
    const diff  = Date.now() - ts;
    const mins  = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days  = Math.floor(diff / 86400000);
    if (mins < 1)   return 'just now';
    if (mins < 60)  return mins + 'm ago';
    if (hours < 24) return hours + 'h ago';
    if (days < 7)   return days + 'd ago';
    return new Date(ts).toLocaleDateString();
}

function escHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function renderSafeMarkdown(markdown) {
    const text = String(markdown || '');
    if (!window.marked || !window.DOMPurify) return escHtml(text);
    const html = marked.parse(text, { breaks: true, gfm: true });
    return DOMPurify.sanitize(html, {
        ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto):|[^a-z]|[a-z+.\-]+(?:[^a-z+.\-:]|$))/i
    });
}

function isSafeBase64Image(value) {
    if (typeof value !== 'string' || value.length === 0 || value.length > 1000000) return false;
    return /^[A-Za-z0-9+/]+={0,2}$/.test(value) && value.length % 4 === 0;
}
