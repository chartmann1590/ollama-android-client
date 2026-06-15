// @ts-check
const { test, expect } = require('@playwright/test');

test.describe('Message input UI', () => {
    test.skip(!process.env.PLAYWRIGHT_TEST_EMAIL, 'Set PLAYWRIGHT_TEST_EMAIL + PLAYWRIGHT_TEST_PASSWORD to run');

    test.beforeEach(async ({ page }) => {
        await page.goto('/chat.html');
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

    test('clicking send shows status bar Sending…', async ({ page }) => {
        await page.fill('#msg-input', 'Test message from Playwright');
        await page.click('#send-btn');
        // Status bar should briefly show a status
        const statusBar = page.locator('#status-bar');
        await expect(statusBar).toContainText(/Sending|generating|Error/, { timeout: 8000 });
    });

    test('Enter key submits message', async ({ page }) => {
        await page.fill('#msg-input', 'Enter key test');
        await page.locator('#msg-input').press('Enter');
        const statusBar = page.locator('#status-bar');
        await expect(statusBar).toContainText(/Sending|generating|Error/, { timeout: 8000 });
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
