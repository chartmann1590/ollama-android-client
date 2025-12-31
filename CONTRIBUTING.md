# Contributing to Ollama Android Client

Thank you for your interest in contributing to Ollama Android Client! This document provides guidelines and instructions for contributing.

## Code of Conduct

By participating in this project, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md).

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check the issue list to see if the bug has already been reported. When creating a bug report, include:

- **Clear title and description**
- **Steps to reproduce** the behavior
- **Expected behavior** vs **actual behavior**
- **Screenshots** if applicable
- **Device information**: Android version, device model
- **App version**: Version name and code

### Suggesting Enhancements

Enhancement suggestions are welcome! Please include:

- **Clear description** of the enhancement
- **Use case**: Why is this enhancement useful?
- **Possible implementation** (if you have ideas)

### Pull Requests

1. **Fork the repository**
2. **Create a feature branch** (`git checkout -b feature/AmazingFeature`)
3. **Follow coding standards** (see below)
4. **Write or update tests** if applicable
5. **Update documentation** if needed
6. **Commit your changes** (`git commit -m 'Add some AmazingFeature'`)
7. **Push to the branch** (`git push origin feature/AmazingFeature`)
8. **Open a Pull Request**

## Development Setup

1. Clone your fork:
   ```bash
   git clone http://10.0.0.129:3000/your-username/ollama-android-client.git
   cd ollama-android-client
   ```

2. Open in Android Studio

3. Create `local.properties` with your SDK path:
   ```properties
   sdk.dir=C\:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
   ```

4. Build and run the project

## Coding Standards

### Kotlin Style Guide

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use 4 spaces for indentation (not tabs)
- Maximum line length: 120 characters
- Use meaningful variable and function names
- Prefer `val` over `var` when possible

### Architecture

- Follow **MVVM** (Model-View-ViewModel) architecture
- Use **Jetpack Compose** for UI
- Use **Hilt** for dependency injection
- Keep UI components in `ui/` package
- Keep business logic in `domain/` package
- Keep data sources in `data/` package

### Code Formatting

- Use Android Studio's built-in formatter (Ctrl+Alt+L / Cmd+Option+L)
- Remove unused imports
- Format code before committing

### Commit Messages

Use clear, descriptive commit messages:

```
feat: Add image attachment support to chat
fix: Resolve crash when server connection fails
docs: Update README with new setup instructions
refactor: Simplify ChatViewModel logic
test: Add unit tests for ChatRepository
```

Follow [Conventional Commits](https://www.conventionalcommits.org/) format:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

## Testing

- Write unit tests for business logic
- Test UI components when possible
- Test on multiple Android versions if possible
- Test with different screen sizes

## Documentation

- Update README.md if you add new features
- Add code comments for complex logic
- Update CHANGELOG.md with your changes

## Review Process

1. All pull requests require review
2. Address review comments promptly
3. Keep pull requests focused and small when possible
4. Respond to feedback constructively

## Questions?

If you have questions, feel free to:
- Open an issue with the `question` label
- Check existing issues and discussions

Thank you for contributing! 🎉

