/**
 * The chat room of exercise 3's bonus, kept in the web client: everybody sees what everybody writes,
 * pulled from the server as a delta like the account's lines (ChatController).
 */
import { el, section } from './dom.js';
import { chatLineText } from './market.js';

export function createChatTab({ api, feed, messenger }) {
    const lines = el('ol', { class: 'chat-lines', id: 'chatLines' });
    const message = el('input', { type: 'text', id: 'chatMessage', placeholder: 'Write to everybody, then press Enter', 'aria-label': 'Message' });
    const send = el('button', { type: 'submit', class: 'primary-button', text: 'Send' });
    const writing = el('form', { class: 'row chat-writing' }, message, send);

    writing.addEventListener('submit', async event => {
        event.preventDefault();
        if (!message.value.trim()) {
            return;
        }
        send.disabled = true;
        try {
            await api.say(message.value);
            message.value = '';
            feed.pullEverything();
        } catch (failure) {
            messenger.refused(failure);
        } finally {
            send.disabled = false;
            message.focus();
        }
    });
    feed.on('chat', show);
    show(feed.chat);
    return { element: el('section', { class: 'pane chat' }, section('Chat with everybody in the market'), lines, writing) };

    function show(all) {
        if (all.length === 0) {
            lines.replaceChildren(el('li', { class: 'placeholder', text: 'Nobody has said anything yet. Say hello.' }));
            return;
        }
        lines.replaceChildren(...all.map(line => el('li', { text: chatLineText(line) })));
        lines.scrollTop = lines.scrollHeight;
    }
}
