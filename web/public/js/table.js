/**
 * Every table on the page. A table is redrawn only when its rows changed, and keeps whichever row was
 * selected — found again by its key, since fresh data brings fresh objects. Redrawing never counts as
 * the user choosing something: only a click does.
 *
 * A column may give each cell a tone — "up" or "down" for money in or out, "live", "waiting" or
 * "closed" for an event's status — which only colours it, the way Polymarket colours its numbers.
 */
import { el } from './dom.js';

export function keepSelection(rows, selectedKey, keyOf) {
    if (selectedKey === null || selectedKey === undefined || !keyOf) {
        return null;
    }
    return rows.find(row => keyOf(row) === selectedKey) ?? null;
}

export const col = (title, value, { wrap = false, tone = null } = {}) => ({ title, value, wrap, tone });
export const num = (title, value, { tone = null } = {}) => ({ title, value, numeric: true, tone });

/** The tone of an event's status, for a column or a chip. */
export const statusTone = status => ({ Active: 'live', 'Not started': 'waiting', Closed: 'closed' })[status] ?? null;

/** The tone of an amount that came in or went out. */
export const moneyTone = amount => (amount > 0.005 ? 'up' : amount < -0.005 ? 'down' : null);

const classes = (...names) => names.filter(Boolean).join(' ') || null;

export function createTable({ columns, keyOf = null, placeholder = '', onSelect = () => {}, id = null }) {
    const body = el('tbody');
    const head = el('thead', {}, el('tr', {}, ...columns.map(column =>
        el('th', { class: classes(column.numeric && 'num'), text: column.title }))));
    const wrap = el('div', { class: 'table-wrap' }, el('table', { class: 'grid', id }, head, body));
    let rows = [];
    let rowElements = [];
    let shownJson = null;
    let selectedKey = null;

    function show(fresh) {
        const json = JSON.stringify(fresh);
        if (json === shownJson) {
            return;
        }
        shownJson = json;
        rows = fresh;
        const kept = keepSelection(rows, selectedKey, keyOf);
        selectedKey = kept ? keyOf(kept) : null;
        rowElements = rows.map(rowElement);
        body.replaceChildren(...(rows.length > 0 ? rowElements
            : [el('tr', {}, el('td', { class: 'placeholder', colspan: String(columns.length), text: placeholder }))]));
    }

    function rowElement(rowData) {
        const selectable = keyOf !== null;
        const tr = el('tr', { class: classes(selectable && 'selectable', selectable && keyOf(rowData) === selectedKey && 'selected') },
            ...columns.map(column => {
                const tone = column.tone ? column.tone(rowData) : null;
                return el('td', {
                    class: classes(column.numeric && 'num', column.wrap && 'wrap', tone && `tone-${tone}`),
                    text: String(column.value(rowData) ?? ''),
                });
            }));
        if (selectable) {
            tr.addEventListener('click', () => choose(rowData));
        }
        return tr;
    }

    function choose(rowData) {
        selectedKey = keyOf(rowData);
        rows.forEach((each, i) => rowElements[i].classList.toggle('selected', keyOf(each) === selectedKey));
        onSelect(rowData);
    }

    return {
        element: wrap,
        show,
        selected: () => keepSelection(rows, selectedKey, keyOf),
        scrollToEnd: () => { wrap.scrollTop = wrap.scrollHeight; },
    };
}

/** A table that is drawn once, with nothing to select. */
export function staticTable(columns, rows, placeholder) {
    const table = createTable({ columns, placeholder });
    table.show(rows);
    return table.element;
}
