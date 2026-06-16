// @ts-check
const { defineConfig, devices } = require('@playwright/test');

// Ensure trailing slash so relative paths like 'chat.html' resolve correctly
const BASE_URL = (process.env.BASE_URL || 'https://chartmann1590.github.io/ollama-android-client').replace(/\/?$/, '/');

module.exports = defineConfig({
    testDir: './tests',
    timeout: 30000,
    retries: process.env.CI ? 2 : 0,
    reporter: [['html', { outputFolder: 'playwright-report', open: 'never' }], ['list']],
    use: {
        baseURL: BASE_URL,
        trace: 'on-first-retry',
        screenshot: 'only-on-failure',
    },
    projects: [
        {
            name: 'chromium',
            use: { ...devices['Desktop Chrome'] },
        },
        // webkit runs locally only — it is too flaky in headless CI
        ...(!process.env.CI ? [{
            name: 'webkit',
            use: { ...devices['Desktop Safari'] },
        }] : []),
    ],
});
