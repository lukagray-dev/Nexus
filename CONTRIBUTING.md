# Contributing to Nexus

Welcome! We are thrilled that you want to help make **Nexus** the best autonomous AI agent for everyone.

This document serves as a comprehensive guide to help you set up your development environment, understand our codebase structure, write code that matches our quality standards, and submit your changes.

## Code of Conduct

By participating in this project, you agree to abide by our [Code of Conduct](./CODE_OF_CONDUCT.md). Please read it to understand the expectations we have for all community members.

## Coding & Design Standards

To maintain a production-grade codebase, please adhere to the following principles:

### 1. Premium Design Aesthetics
*   **No Placeholders**: Never include empty placeholders, broken links, or empty mock screens.
*   **No Emojis in UI**: When building GUI elements, use professional-grade vector SVGs, PNGs, or drawables. Emojis look different across operating systems and detract from the premium feel.
*   **Vibrant Styles**: Use HSL color schemes, curated dark-mode palettes, and modern typography (e.g., *Inter*, *PT Serif* or *Outfit*).

### 2. Production Code Quality
*   **Detailed Inline Comments**: Write comments that explain the **why** behind complex decisions. Imagine you are explaining the code to a newbie developer friend. Explain assumptions, edge cases, and design constraints.
*   **Deterministic Logic**: Design functions to be predictable, well-structured, and strictly type-safe. Avoid unsafe blocks or raw unwraps unless thoroughly documented and tested.
*   **Separation of Concerns**: Ensure layers are well-separated.

---

## Submitting Your Contribution

We follow a typical feature-branch workflow. Please follow these steps to submit your changes:

### 1. Branch Naming
Create a new branch:
*   Features: `feat/your-feature-name`
*   Bug fixes: `fix/bug-description`
*   Documentation: `docs/what-changed`

### 2. Commit Guidelines
We use semantic commit messages to automatically build our changelogs. Please format your commit messages like this:

*   `feat(gui): implement custom sidebar chevron resizing`
*   `fix(core): resolve nvidia nim api model discovery crash`
*   `docs: update contributing guidelines`
*   `refactor(tui): split state machine into screen controllers`

### 3. Submitting the PR
1.  Push your branch to your fork:
    ```bash
    git push origin feat/your-feature-name
    ```
2.  Open a Pull Request pointing **to the `dev` branch** of the main Operon repository.
3.  **Do not merge to `main` directly**: The `main` branch is reserved for releases. Merging into `main` automatically triggers the release workflow and compiles binaries. 

---

Thank you for contributing to Nexus! If you have any questions, feel free to open a discussion or reach out to us at `heylukagray@gmail.com`.
