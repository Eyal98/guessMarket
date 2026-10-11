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

/**
 * Whether fresh rows are the rows already shown with new values — same count, same keys, same order —
 * so they can be written into the rows on the screen rather than replacing them.
 */
export function fillsInPlace(previous, fresh, keyOf) {
    return previous.length === fresh.length && fresh.length > 0
        && (!keyOf || fresh.every((each, i) => keyOf(each) === keyOf(previous[i])));
}

export const col =(title, value, { wrap = false, tone = null } = {}) => ({ title, value, wrap, tone });
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
        const sameRows = fillsInPlace(rows, fresh, keyOf);
        shownJson = json;
        rows = fresh;
        const kept = keepSelection(rows, selectedKey, keyOf);
        selectedKey = kept ? keyOf(kept) : null;
        if (sameRows) {
            // The same rows with new values - prices moving - are written into the rows already on the
            // screen. Replacing them would swallow a click that happened to land in between.
            rows.forEach((each, i) => fill(rowElements[i], each));
            return;
        }
        rowElements = rows.map((each, i) => rowElement(i));
        rows.forEach((each, i) => fill(rowElements[i], each));
        body.replaceChildren(...(rows.length > 0 ? rowElements
            : [el('tr', {}, el('td', { class: 'placeholder', colspan: String(columns.length), text: placeholder }))]));
    }

    function rowElement(index) {
        const tr = el('tr', { class: keyOf ? 'selectable' : null }, ...columns.map(() => el('td')));
        if (keyOf) {
            tr.addEventListener('click', () => choose(rows[index]));
        }
        return tr;
    }

    function fill(tr, rowData) {
        if (keyOf) {
            tr.classList.toggle('selected', keyOf(rowData) === selectedKey);
        }
        columns.forEach((column, i) => {
            const cell = tr.cells[i];
            const tone = column.tone ? column.tone(rowData) : null;
            cell.className = classes(column.numeric && 'num', column.wrap && 'wrap', tone && `tone-${tone}`) ?? '';
            cell.textContent = String(column.value(rowData) ?? '');
        });
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
