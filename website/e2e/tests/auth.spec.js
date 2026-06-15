// @ts-check
const { test, expect } = require('@playwright/test');

test.describe('Chat page — auth wall', () => {
    test('page loads and returns 200', async ({ page }) => {
        const res = await page.goto('chat.html');
        expect(res.status()).toBe(200);
    });

    test('auth section is visible when not signed in', async ({ page }) => {
        await page.goto('chat.html');
        await expect(page.locator('#auth-section')).toBeVisible();
    });

    test('chat shell is hidden when not signed in', async ({ page }) => {
        await page.goto('chat.html');
        await expect(page.locator('#chat-shell')).not.toHaveClass(/visible/);
    });

    test('sign-in form elements are present', async ({ page }) => {
        await page.goto('chat.html');
        await expect(page.locator('#btn-google')).toBeVisible();
        await expect(page.locator('#auth-email')).toBeVisible();
        await expect(page.locator('#auth-password')).toBeVisible();
        await expect(page.locator('#btn-signin')).toBeVisible();
        await expect(page.locator('#btn-create')).toBeVisible();
        await expect(page.locator('#btn-forgot')).toBeVisible();
    });

    test('info box about phone requirement is shown', async ({ page }) => {
        await page.goto('chat.html');
        const infoBox = page.locator('.auth-info-box');
        await expect(infoBox).toBeVisible();
        await expect(infoBox).toContainText('Web Sync');
    });

    test('shows error on empty email sign-in attempt', async ({ page }) => {
        await page.goto('chat.html');
        await page.click('#btn-signin');
        await expect(page.locator('#auth-error')).toBeVisible();
        await expect(page.locator('#auth-error')).toContainText('email and password');
    });

    test('Google sign-in button is clickable', async ({ page }) => {
        await page.goto('chat.html');
        // Clicking opens a popup — just verify the button is enabled and reachable
        await expect(page.locator('#btn-google')).toBeEnabled();
    });
});

test.describe('Chat page — email sign-in with test account', () => {
    test.skip(!process.env.PLAYWRIGHT_TEST_EMAIL, 'Set PLAYWRIGHT_TEST_EMAIL + PLAYWRIGHT_TEST_PASSWORD to run');

    test('signs in and shows chat shell', async ({ page }) => {
        await page.goto('chat.html');

        await page.fill('#auth-email', process.env.PLAYWRIGHT_TEST_EMAIL);
        await page.fill('#auth-password', process.env.PLAYWRIGHT_TEST_PASSWORD);
        await page.click('#btn-signin');

        // Wait for Firebase auth + chat shell to appear (up to 15s)
        await expect(page.locator('#chat-shell')).toHaveClass(/visible/, { timeout: 15000 });
        await expect(page.locator('#auth-section')).not.toBeVisible();
    });

    test('shows user email in sidebar after sign-in', async ({ page }) => {
        await page.goto('chat.html');
        await page.fill('#auth-email', process.env.PLAYWRIGHT_TEST_EMAIL);
        await page.fill('#auth-password', process.env.PLAYWRIGHT_TEST_PASSWORD);
        await page.click('#btn-signin');

        await expect(page.locator('#chat-shell')).toHaveClass(/visible/, { timeout: 15000 });
        const email = page.locator('#user-email');
        await expect(email).toContainText(process.env.PLAYWRIGHT_TEST_EMAIL.split('@')[0]);
    });

    test('sign-out button returns to auth wall', async ({ page }) => {
        await page.goto('chat.html');
        await page.fill('#auth-email', process.env.PLAYWRIGHT_TEST_EMAIL);
        await page.fill('#auth-password', process.env.PLAYWRIGHT_TEST_PASSWORD);
        await page.click('#btn-signin');
        await expect(page.locator('#chat-shell')).toHaveClass(/visible/, { timeout: 15000 });

        await page.click('#btn-signout');
        await expect(page.locator('#auth-section')).toBeVisible({ timeout: 5000 });
        await expect(page.locator('#chat-shell')).not.toHaveClass(/visible/);
    });
});
