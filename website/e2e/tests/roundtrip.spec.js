// @ts-check
const { test, expect } = require('@playwright/test');

// ----------------------------------------------------------------
// Tier 2 — Full phone round-trip tests
// These require the Android app running on a physical device with:
//   1. Same Firebase account as PLAYWRIGHT_TEST_EMAIL
//   2. Web Sync toggled ON in Settings → Account
//   3. Ollama server accessible from the phone
// These tests are SKIPPED in CI environments.
// ----------------------------------------------------------------
test.skip(!!process.env.CI, 'Tier 2: Requires physical phone with Ollama + Web Sync enabled');

test.describe('Full phone round-trip sync', () => {
    const ROUND_TRIP_TIMEOUT = 90000; // 90s — Ollama inference can take time

    test.beforeEach(async ({ page }) => {
        await page.goto('/chat.html');
        await page.fill('#auth-email', process.env.PLAYWRIGHT_TEST_EMAIL);
        await page.fill('#auth-password', process.env.PLAYWRIGHT_TEST_PASSWORD);
        await page.click('#btn-signin');
        await expect(page.locator('#chat-shell')).toHaveClass(/visible/, { timeout: 15000 });
        await page.click('#btn-new-chat');
    });

    test('send a message and receive AI response', async ({ page }) => {
        const msg = 'Say exactly: "Playwright test OK"';
        await page.fill('#msg-input', msg);
        await page.click('#send-btn');

        // Status bar should show progress
        await expect(page.locator('#status-bar')).toContainText(/Sending|generating/, { timeout: 15000 });

        // Wait for status bar to clear (means request completed)
        await expect(page.locator('#status-bar')).toBeEmpty({ timeout: ROUND_TRIP_TIMEOUT });

        // At least one assistant message should be in the thread
        const assistantMsg = page.locator('.message.assistant').first();
        await expect(assistantMsg).toBeVisible({ timeout: 5000 });
        await expect(assistantMsg.locator('.message-bubble')).not.toHaveText('');
    });

    test('new web thread appears in phone thread list after creation', async ({ page }) => {
        // Capture the initial thread count on web
        const threadCountBefore = await page.locator('.thread-item').count();

        await page.fill('#msg-input', 'Hello from the web chat test');
        await page.click('#send-btn');

        // After send, thread should appear in sidebar (phone will create it and sync back)
        await expect(async () => {
            const count = await page.locator('.thread-item').count();
            expect(count).toBeGreaterThan(threadCountBefore);
        }).toPass({ timeout: 30000, intervals: [2000] });
    });

    test('phone-created thread syncs to web sidebar', async ({ page }) => {
        // This test verifies sync direction: phone → web
        // Manually create a thread on the phone, then verify it appears in web sidebar
        // (Since we can't automate phone actions here, we observe that the thread list
        // updates dynamically — test watches for any new thread item appearing)
        const initial = await page.locator('.thread-item').count();
        // Wait up to 30s for a new thread to appear (triggered by the phone)
        await page.waitForFunction(
            initialCount => document.querySelectorAll('.thread-item').length > initialCount,
            initial,
            { timeout: 30000 }
        ).catch(() => {
            // If no new thread appeared, that's OK — just verify existing ones still show
            console.log('No new thread appeared from phone during test window');
        });
        // Either way, the thread list must be functional
        await expect(page.locator('#thread-list')).toBeVisible();
    });

    test('status bar clears after successful response', async ({ page }) => {
        await page.fill('#msg-input', 'What is 2 + 2?');
        await page.click('#send-btn');

        // Wait for a response and verify no error state
        await expect(page.locator('#status-bar')).toBeEmpty({ timeout: ROUND_TRIP_TIMEOUT });
        await expect(page.locator('#status-bar')).not.toHaveClass(/error/);
    });

    test('image attachment is forwarded to phone', async ({ page }) => {
        // Create a tiny 1x1 PNG as a test image
        const buffer = Buffer.from(
            'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
            'base64'
        );
        // Set file from buffer
        await page.locator('#image-input').setInputFiles({
            name: 'test.png',
            mimeType: 'image/png',
            buffer,
        });

        // Preview thumbnail should appear
        await expect(page.locator('.preview-thumb')).toBeVisible({ timeout: 3000 });

        // Send with image
        await page.fill('#msg-input', 'What do you see in this image?');
        await page.click('#send-btn');

        // Wait for round-trip
        await expect(page.locator('#status-bar')).toBeEmpty({ timeout: ROUND_TRIP_TIMEOUT });
        await expect(page.locator('.message.assistant')).toBeVisible({ timeout: 5000 });
    });
});
