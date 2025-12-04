// Login page JavaScript

document.addEventListener('DOMContentLoaded', () => {
    const loginForm = document.getElementById('login-form');
    const registerForm = document.getElementById('register-form');
    const authStatus = document.getElementById('auth-status');

    // Handle login
    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();

        const username = document.getElementById('login-username').value;
        const password = document.getElementById('login-password').value;

        authStatus.textContent = '登录中...';
        authStatus.style.color = '#333';

        try {
            const response = await fetch('/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });

            if (response.ok) {
                const user = await response.json();
                // Store username in localStorage for replay viewer
                localStorage.setItem('username', user.username);

                // Redirect to main page
                window.location.href = 'index.html';
            } else {
                const errorText = await response.text();
                authStatus.textContent = '登录失败: ' + errorText;
                authStatus.style.color = '#d9534f';
            }
        } catch (error) {
            console.error('Login error:', error);
            authStatus.textContent = '登录失败，请重试';
            authStatus.style.color = '#d9534f';
        }
    });

    // Handle registration
    registerForm.addEventListener('submit', async (e) => {
        e.preventDefault();

        const username = document.getElementById('register-username').value;
        const password = document.getElementById('register-password').value;

        // Validate inputs
        const usernameInput = document.getElementById('register-username');
        const passwordInput = document.getElementById('register-password');

        if (!usernameInput.checkValidity()) {
            authStatus.textContent = usernameInput.validationMessage;
            authStatus.style.color = '#d9534f';
            return;
        }

        if (!passwordInput.checkValidity()) {
            authStatus.textContent = passwordInput.validationMessage;
            authStatus.style.color = '#d9534f';
            return;
        }

        authStatus.textContent = '注册中...';
        authStatus.style.color = '#333';

        try {
            const response = await fetch('/api/auth/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });

            if (response.ok) {
                authStatus.textContent = '注册成功！请登录。';
                authStatus.style.color = '#28a745';
                registerForm.reset();
            } else {
                const errorText = await response.text();
                authStatus.textContent = '注册失败: ' + errorText;
                authStatus.style.color = '#d9534f';
            }
        } catch (error) {
            console.error('Registration error:', error);
            authStatus.textContent = '注册失败，请重试';
            authStatus.style.color = '#d9534f';
        }
    });
});
