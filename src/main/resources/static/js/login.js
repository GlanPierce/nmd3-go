// Login page JavaScript - Global functions for single form

async function login(username, password) {
    const authStatus = document.getElementById('auth-status');
    const loadingOverlay = document.getElementById('loading-overlay');

    // Show loading overlay
    if (loadingOverlay) {
        loadingOverlay.classList.add('active');
    }

    // Clear previous errors
    if (authStatus) {
        authStatus.textContent = '';
    }

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

            // Redirect to main page (keep overlay active)
            window.location.href = 'index.html';
        } else {
            // Hide overlay on error
            if (loadingOverlay) {
                loadingOverlay.classList.remove('active');
            }

            const errorText = await response.text();
            if (authStatus) {
                authStatus.textContent = errorText;
            }
        }
    } catch (error) {
        console.error('Login error:', error);
        // Hide overlay on error
        if (loadingOverlay) {
            loadingOverlay.classList.remove('active');
        }
        if (authStatus) {
            authStatus.textContent = '登录失败，请重试';
        }
    }
}

async function register(username, password) {
    const regStatus = document.getElementById('reg-status');
    const loadingOverlay = document.getElementById('loading-overlay');

    // Basic validation
    if (username.length < 3 || username.length > 16) {
        regStatus.textContent = '用户名长度需为3-16位';
        return;
    }
    if (password.length < 8) {
        regStatus.textContent = '密码至少8位';
        return;
    }

    // Show loading overlay
    if (loadingOverlay) {
        loadingOverlay.classList.add('active');
    }

    // Clear previous errors
    if (regStatus) {
        regStatus.textContent = '';
    }

    try {
        const response = await fetch('/api/auth/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });

        if (response.ok) {
            // Keep overlay active for auto-login
            // Auto login
            await login(username, password);
        } else {
            // Hide overlay on error
            if (loadingOverlay) {
                loadingOverlay.classList.remove('active');
            }

            const errorText = await response.text();
            regStatus.textContent = errorText;
        }
    } catch (error) {
        console.error('Registration error:', error);
        // Hide overlay on error
        if (loadingOverlay) {
            loadingOverlay.classList.remove('active');
        }
        regStatus.textContent = '注册失败，请重试';
    }
}
