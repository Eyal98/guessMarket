/**
 * The few building blocks every screen is made of. Text always goes in as text — textContent or a
 * text node — never as HTML, so whatever a user named an event is shown as the name it is.
 */
export function el(tag, props = {}, ...children) {
    const node = document.createElement(tag);
    for (const [name, value] of Object.entries(props)) {
        if (value === null || value === undefined || value === false) {
            continue;
        }
        if (name === 'text') {
            node.textContent = value;
        } else if (name === 'class') {
            node.className = value;
        } else if (name === 'value') {
            node.value = value;
        } else if (name.startsWith('on') && typeof value === 'function') {
            node.addEventListener(name.slice(2), value);
        } else {
            node.setAttribute(name, value === true ? '' : String(value));
        }
    }
    node.append(...children.flat().filter(child => child !== null && child !== undefined && child !== false));
    return node;
}

/** Views.section. */
export const section = title => el('h3', { class: 'section-title', text: title });

/** Views.wrapping. */
export const wrapping = text => el('p', { class: 'wrapping', text });

/** Views.labelled: caption, value, caption, value… */
export function labelled(...pairs) {
    const list = el('dl', { class: 'labelled' });
    for (let i = 0; i < pairs.length; i += 2) {
        list.append(el('dt', { text: pairs[i] }), el('dd', { text: String(pairs[i + 1] ?? '') }));
    }
    return list;
}

export const row = (...children) => el('div', { class: 'row' }, ...children);

export const label = text => el('span', { class: 'caption', text });

/** Tabs, the first one chosen. */
export function tabbed(panels) {
    const buttons = new Map();
    const holders = new Map();
    for (const { name, element } of panels) {
        buttons.set(name, el('button', { type: 'button', role: 'tab', class: 'tab', text: name, onclick: () => select(name) }));
        holders.set(name, el('div', { class: 'tab-panel', role: 'tabpanel' }, element));
    }
    function select(chosen) {
        for (const [name, button] of buttons) {
            button.setAttribute('aria-selected', String(name === chosen));
            holders.get(name).hidden = name !== chosen;
        }
    }
    select(panels[0].name);
    return {
        element: el('div', { class: 'tabbed' },
            el('nav', { class: 'tabs', role: 'tablist' }, ...buttons.values()),
            el('div', { class: 'tab-panels' }, ...holders.values())),
        select,
    };
}
