/** The login screen (login.fxml, LoginController): a name, and nothing else. */
import { el } from './dom.js';

export function showLogin(main, { api, serverAddress, message = '', name = '', loggedIn }) {
    const field = el('input', { type: 'text', id: 'nameField', placeholder: 'Your name', 'aria-label': 'Your name', autocomplete: 'off', value: name });
    const button = el('button', { type: 'submit', id: 'loginButton', class: 'primary-button', text: 'Log in' });
    const problem = el('p', { id: 'problemLabel', class: 'problem', role: 'alert', text: message });
    const form = el('form', { class: 'login-card card' },
        el('h2', { class: 'detail-title', text: 'Log in' }),
        el('p', { class: 'caption', text: 'Choose a name. If somebody is already using it, you will be asked for another.' }),
        el('div', { class: 'row' }, field, button),
        problem,
        el('p', { class: 'caption small', text: `Server: ${serverAddress}` }));

    form.addEventListener('submit', async event => {
        event.preventDefault();
        const wanted = field.value.trim();
        if (!wanted) {
            problem.textContent = 'Please enter a name.';
            return;
        }
        button.disabled = true;
        field.disabled = true;
        problem.textContent = '';
        try {
            loggedIn(await api.login(wanted));
        } catch (failure) {
            button.disabled = false;
            field.disabled = false;
            problem.textContent = failure.message;
            field.select();
            field.focus();
        }
    });

    main.replaceChildren(el('div', { class: 'login-page' }, form));
    field.focus();
}
