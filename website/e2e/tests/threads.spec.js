// @ts-check
const { test, expect } = require('@playwright/test');

// These tests require a signed-in test account
test.describe('Thread list', () => {
    test.skip(!process.env.PLAYWRIGHT_TEST_EMAIL, 'Set PLAYWRIGHT_TEST_EMAIL + PLAYWRIGHT_TEST_PASSWORD to run');

    test.beforeEach(async ({ page }) => {
        await page.goto('chat.html');
        await page.fill('#auth-email', process.env.PLAYWRIGHT_TEST_EMAIL);
        await page.fill('#auth-password', process.env.PLAYWRIGHT_TEST_PASSWORD);
        await page.click('#btn-signin');
        await expect(page.locator('#chat-shell')).toHaveClass(/visible/, { timeout: 15000 });
    });

    test('thread list renders (or shows empty state)', async ({ page }) => {
        const threadList = page.locator('#thread-list');
        await expect(threadList).toBeVisible();
        // Wait for skeleton to be replaced with real content or empty message
        await page.waitForFunction(() => {
            const el = document.getElementById('thread-list');
            return el && !el.querySelector('.thread-skeleton');
        }, { timeout: 10000 });
        await expect(threadList).toBeVisible();
    });

    test('New Chat button creates empty chat state', async ({ page }) => {
        await page.click('#btn-new-chat');
        await expect(page.locator('#chat-header')).toBeVisible();
        const title = page.locator('#chat-header-title');
        await expect(title).toHaveText('New Chat');
    });

    test('clicking a thread opens message view', async ({ page }) => {
        // Wait for real threads to load
        await page.waitForSelector('.thread-item', { timeout: 10000 });
        await page.locator('.thread-item').first().click();

        // Chat header should appear with a title
        await expect(page.locator('#chat-header')).toBeVisible();
        const title = page.locator('#chat-header-title');
        await expect(title).not.toHaveText('');

        // Message list should be visible
        await expect(page.locator('#message-list')).toBeVisible();
    });

    test('active thread gets active CSS class in sidebar', async ({ page }) => {
        await page.waitForSelector('.thread-item', { timeout: 10000 });
        const firstThread = page.locator('.thread-item').first();
        await firstThread.click();
        await expect(firstThread).toHaveClass(/active/);
    });

    test('phone status indicator is visible in sidebar footer', async ({ page }) => {
        await expect(page.locator('.phone-status')).toBeVisible();
        await expect(page.locator('#phone-dot')).toBeVisible();
        await expect(page.locator('#phone-label')).toBeVisible();
    });
});
