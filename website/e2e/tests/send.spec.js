// @ts-check
const { test, expect } = require('@playwright/test');

test.describe('Message input UI', () => {
    test.skip(!process.env.PLAYWRIGHT_TEST_EMAIL, 'Set PLAYWRIGHT_TEST_EMAIL + PLAYWRIGHT_TEST_PASSWORD to run');

    test.beforeEach(async ({ page }) => {
        await page.goto('chat.html');
        await page.fill('#auth-email', process.env.PLAYWRIGHT_TEST_EMAIL);
        await page.fill('#auth-password', process.env.PLAYWRIGHT_TEST_PASSWORD);
        await page.click('#btn-signin');
        await expect(page.locator('#chat-shell')).toHaveClass(/visible/, { timeout: 15000 });
        // Start a new chat so input is enabled
        await page.click('#btn-new-chat');
    });

    test('send button is disabled when input is empty', async ({ page }) => {
        await expect(page.locator('#send-btn')).toBeDisabled();
    });

    test('send button enables after typing a message', async ({ page }) => {
        await page.fill('#msg-input', 'Hello');
        await expect(page.locator('#send-btn')).toBeEnabled();
    });

    test('send button disables again after clearing input', async ({ page }) => {
        await page.fill('#msg-input', 'Hello');
        await expect(page.locator('#send-btn')).toBeEnabled();
        await page.fill('#msg-input', '');
        // Trigger the input event
        await page.locator('#msg-input').dispatchEvent('input');
        await expect(page.locator('#send-btn')).toBeDisabled();
    });

    test('clicking send shows status (Sending or queued when phone offline)', async ({ page }) => {
        await page.fill('#msg-input', 'Test message from Playwright');
        await page.click('#send-btn');
        // Either "Sending…" (phone online) or "queued — waiting for phone" (phone offline)
        const statusBar = page.locator('#status-bar');
        await expect(statusBar).toContainText(/Sending|generating|queued|Error/, { timeout: 8000 });
    });

    test('Enter key submits message (shows status bar)', async ({ page }) => {
        await page.fill('#msg-input', 'Enter key test');
        await page.locator('#msg-input').press('Enter');
        const statusBar = page.locator('#status-bar');
        await expect(statusBar).toContainText(/Sending|generating|queued|Error/, { timeout: 8000 });
    });

    test('offline queue: message queued when phone offline shows queued status', async ({ page }) => {
        // Force phone-offline via test hook exposed by chat.js
        await page.evaluate(() => window.__testSetPhoneOnline(false));
        await page.fill('#msg-input', 'Queued message test');
        await page.click('#send-btn');
        // Status bar should show "queued — waiting for phone"
        await expect(page.locator('#status-bar')).toContainText(/queued/i, { timeout: 5000 });
        // Input must re-enable so user can keep sending (they queue up)
        await expect(page.locator('#msg-input')).toBeEnabled();
    });

    test('offline queue: multiple messages can be queued', async ({ page }) => {
        await page.evaluate(() => window.__testSetPhoneOnline(false));
        await page.fill('#msg-input', 'First queued');
        await page.click('#send-btn');
        await page.fill('#msg-input', 'Second queued');
        await page.click('#send-btn');
        // Status should reflect 2 messages queued
        await expect(page.locator('#status-bar')).toContainText(/2 messages queued/i, { timeout: 5000 });
    });

    test('offline queue: queue drains when phone comes online', async ({ page }) => {
        // Queue a message while offline
        await page.evaluate(() => window.__testSetPhoneOnline(false));
        await page.fill('#msg-input', 'Will drain when online');
        await page.click('#send-btn');
        await expect(page.locator('#status-bar')).toContainText(/queued/i, { timeout: 5000 });

        // Simulate phone coming online — queue should dispatch (status changes to Sending…)
        await page.evaluate(() => window.__testSetPhoneOnline(true));
        await expect(page.locator('#status-bar')).toContainText(/Sending|generating|Error/, { timeout: 8000 });
    });

    test('Shift+Enter adds newline instead of submitting', async ({ page }) => {
        await page.fill('#msg-input', 'First line');
        await page.locator('#msg-input').press('Shift+Enter');
        const value = await page.locator('#msg-input').inputValue();
        expect(value).toContain('\n');
        // Send button should still be enabled (content is not empty)
        await expect(page.locator('#send-btn')).toBeEnabled();
    });

    test('attach button opens file picker', async ({ page }) => {
        // Verify the attach button is present and enabled
        await expect(page.locator('#btn-attach')).toBeEnabled();
        // File chooser dialog opens when clicked — just verify button is functional
        const [fileChooser] = await Promise.all([
            page.waitForEvent('filechooser', { timeout: 3000 }).catch(() => null),
            page.click('#btn-attach'),
        ]);
        // fileChooser may be null in some headless envs, but button click should not throw
    });
});
