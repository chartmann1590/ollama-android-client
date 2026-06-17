/* =========================================================
   Ollama AI Chat — Admin Dashboard
   Restricted to a single administrator account. All data
   access is additionally enforced by Firebase security rules
   (root read is granted only to the admin email), so this
   client-side gate is a UX convenience, not the security
   boundary.
   ========================================================= */

const ADMIN_EMAIL = 'charles.h.hartmann1@gmail.com';
const GH_OWNER = 'chartmann1590';
const GH_REPO = 'ollama-android-client';

// Firebase config is injected by the GitHub Actions deploy step.
const firebaseConfig = __FIREBASE_CONFIG__;

let auth = null, db = null;
try {
    firebase.initializeApp(firebaseConfig);
    if (firebaseConfig.appCheckSiteKey && firebase.appCheck) {
        firebase.appCheck().activate(firebaseConfig.appCheckSiteKey, true);
    }
    auth = firebase.auth();
    db = firebase.database();
} catch (e) {
    console.warn('Firebase init failed:', e.message);
}

const $ = id => document.getElementById(id);
const gate = $('gate'), app = $('app');

function showGate(message, isError) {
    gate.style.display = 'block';
    app.style.display = 'none';
    if (message) $('gate-msg').textContent = message;
    $('gate-err').textContent = isError ? message : '';
    $('btn-gate-signout').style.display = isError ? 'inline-flex' : 'none';
    $('btn-google').style.display = isError ? 'none' : 'inline-flex';
}

function showApp(user) {
    gate.style.display = 'none';
    app.style.display = 'block';
    $('who').textContent = user.email;
    loadAll();
}

$('btn-google').addEventListener('click', () => {
    if (!auth) { showGate('Firebase is not configured on this deployment.', true); return; }
    auth.signInWithPopup(new firebase.auth.GoogleAuthProvider())
        .catch(err => showGate(err.message, true));
});
$('btn-signout').addEventListener('click', () => auth && auth.signOut());
$('btn-gate-signout').addEventListener('click', () => auth && auth.signOut());
$('btn-refresh').addEventListener('click', () => loadAll());

(auth || { onAuthStateChanged: () => {} }).onAuthStateChanged(user => {
    if (!user) { showGate('Sign in with the administrator account to continue.'); return; }
    if (user.email !== ADMIN_EMAIL) {
        showGate('This account (' + user.email + ') is not authorized for the admin dashboard.', true);
        return;
    }
    showApp(user);
});

// ---- Data loading ----
async function loadAll() {
    setStats({});
    loadReports();
    loadUsers();
    loadIssues();
}

function fmtTime(ms) {
    if (!ms) return '—';
    try { return new Date(Number(ms)).toLocaleString(); } catch (e) { return String(ms); }
}

function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, c => (
        { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
    ));
}

const stats = {};
function setStats(patch) {
    Object.assign(stats, patch);
    const cards = [
        ['users', 'Users'],
        ['premium', 'Web Sync premium'],
        ['threads', 'Synced threads'],
        ['messages', 'Synced messages'],
        ['onlineDevices', 'Devices online'],
        ['reports', 'Content reports'],
        ['openIssues', 'Open issues'],
    ];
    $('stats').innerHTML = cards.map(([k, l]) =>
        `<div class="card"><div class="n">${stats[k] != null ? stats[k] : '…'}</div><div class="l">${l}</div></div>`
    ).join('');
}

// --- Content reports ---
function loadReports() {
    if (!db) return;
    db.ref('contentReports').once('value').then(snap => {
        const val = snap.val() || {};
        const entries = Object.entries(val).sort((a, b) => (b[1].reportedAt || 0) - (a[1].reportedAt || 0));
        $('reports-count').textContent = entries.length;
        setStats({ reports: entries.length });
        if (entries.length === 0) { $('reports').innerHTML = '<p class="muted">No reports yet.</p>'; return; }
        $('reports').innerHTML = entries.map(([id, r]) => `
            <div class="report">
                <div class="meta">
                    <span>🕓 ${fmtTime(r.reportedAt)}</span>
                    <span>reason: <strong>${esc(r.reason || 'unspecified')}</strong></span>
                    <span class="mono">${esc(r.package || '')}</span>
                    <button class="btn danger" data-del="${esc(id)}">Delete</button>
                </div>
                <p class="snip">${esc(r.snippet || '')}</p>
            </div>`).join('');
        document.querySelectorAll('[data-del]').forEach(btn => {
            btn.addEventListener('click', () => {
                if (!window.confirm('Delete this report?')) return;
                db.ref('contentReports/' + btn.getAttribute('data-del')).remove().then(loadReports);
            });
        });
    }).catch(err => { $('reports').innerHTML = `<p class="err">${esc(err.message)}</p>`; });
}

// --- Users ---
function loadUsers() {
    if (!db) return;
    db.ref('users').once('value').then(snap => {
        const users = snap.val() || {};
        const uids = Object.keys(users);
        let premium = 0, threads = 0, messages = 0, online = 0;
        const now = Date.now();
        const rows = uids.map(uid => {
            const u = users[uid] || {};
            const isPremium = !!(u.subscription && u.subscription.webSyncPremium);
            if (isPremium) premium++;
            const tCount = u.threads ? Object.keys(u.threads).length : 0;
            threads += tCount;
            let mCount = 0;
            if (u.messages) Object.values(u.messages).forEach(t => { mCount += t ? Object.keys(t).length : 0; });
            messages += mCount;
            const devices = u.devices ? Object.values(u.devices) : [];
            const isOnline = devices.some(d => d && d.updatedAt && (now - d.updatedAt) < 120000);
            if (isOnline) online++;
            const models = u.availableModels ? Object.keys(u.availableModels).length : 0;
            const usage = u.webSyncDailyUsage || {};
            return `<tr>
                <td class="mono">${esc(uid.slice(0, 12))}…</td>
                <td><span class="pill ${isPremium ? 'yes' : 'no'}">${isPremium ? 'premium' : 'free'}</span></td>
                <td><span class="pill ${isOnline ? 'yes' : 'no'}">${isOnline ? 'online' : 'offline'}</span></td>
                <td>${tCount}</td>
                <td>${mCount}</td>
                <td>${models}</td>
                <td>${esc(usage.count != null ? usage.count + ' (' + (usage.date || '') + ')' : '—')}</td>
            </tr>`;
        });
        $('users-count').textContent = uids.length;
        setStats({ users: uids.length, premium, threads, messages, onlineDevices: online });
        $('users').innerHTML = uids.length === 0 ? '<p class="muted">No users yet.</p>' : `
            <table><thead><tr>
                <th>UID</th><th>Plan</th><th>Status</th><th>Threads</th><th>Messages</th><th>Models</th><th>Web usage</th>
            </tr></thead><tbody>${rows.join('')}</tbody></table>`;
    }).catch(err => { $('users').innerHTML = `<p class="err">${esc(err.message)}</p>`; });
}

// --- GitHub issues (public repo; user feedback is filed as issues) ---
async function loadIssues() {
    try {
        const resp = await fetch(`https://api.github.com/repos/${GH_OWNER}/${GH_REPO}/issues?state=all&per_page=100`);
        if (!resp.ok) throw new Error('GitHub API ' + resp.status);
        const all = await resp.json();
        const issues = all.filter(i => !i.pull_request); // exclude PRs
        const openCount = issues.filter(i => i.state === 'open').length;
        $('issues-count').textContent = issues.length;
        setStats({ openIssues: openCount });
        if (issues.length === 0) { $('issues').innerHTML = '<p class="muted">No issues.</p>'; return; }
        $('issues').innerHTML = `
            <table><thead><tr>
                <th>#</th><th>Title</th><th>State</th><th>By</th><th>Opened</th><th>Comments</th>
            </tr></thead><tbody>${issues.map(i => `
                <tr>
                    <td>${i.number}</td>
                    <td><a class="issue-link" href="${esc(i.html_url)}" target="_blank" rel="noopener">${esc(i.title)}</a></td>
                    <td><span class="pill ${i.state}">${i.state}</span></td>
                    <td class="mono">${esc(i.user ? i.user.login : '')}</td>
                    <td>${fmtTime(Date.parse(i.created_at))}</td>
                    <td>${i.comments}</td>
                </tr>`).join('')}</tbody></table>`;
    } catch (err) {
        $('issues').innerHTML = `<p class="err">${esc(err.message)}</p>`;
    }
}
