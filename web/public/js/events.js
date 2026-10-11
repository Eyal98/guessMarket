/**
 * The events tab: every event on the server on the left with the filters above them, and the selected
 * one on the right with the means of acting on it (EventsController).
 */
import { el } from './dom.js';
import { createTable, col, num, statusTone } from './table.js';
import { money } from './format.js';
import { ALL, FILTERS, passesFilters, eventsSummary, commissionOf } from './market.js';
import { createTradePanel } from './trade.js';
import { createEventDetail } from './event-detail.js';

export function createEventsTab({ api, feed, messenger }) {
    const trade = createTradePanel({ api, feed, messenger });
    const detail = createEventDetail({ api, feed, actions: trade.element });
    const chosen = Object.fromEntries(FILTERS.map(filter => [filter.field, ALL]));
    const summary = el('p', { class: 'summary' });
    const table = createTable({
        id: 'eventsTable',
        columns: [
            num('#', event => event.number), col('Name', event => event.name),
            col('Status', event => event.status, { tone: event => statusTone(event.status) }),
            col('Type', event => event.methodKind), col('Commission', commissionOf),
            num('Account', event => money(event.accountBalance)), col('Market maker', event => event.marketMakerName),
        ],
        keyOf: event => event.number,
        placeholder: 'No events yet. Upload an events file from the JavaFX client.',
        onSelect: showSelected,
    });
    table.element.classList.add('grow');

    const element = el('div', { class: 'split events-split' },
        el('section', { class: 'pane' }, filterBar(), summary, table.element), detail.element);
    feed.on('events', refresh);
    refresh();
    return { element };

    function refresh() {
        const passing = feed.events.filter(event => passesFilters(event, chosen));
        table.show(passing);
        summary.textContent = eventsSummary(passing.length, feed.events.length);
        showSelected();
    }

    function showSelected() {
        const selected = table.selected();
        detail.watch(selected);
        trade.show(selected);
    }

    /** One group of toggles per filter, exactly one always chosen, "All" to begin with. */
    function filterBar() {
        return el('div', { class: 'filters' }, ...FILTERS.map(filter => {
            const buttons = filter.choices.map(choice => el('button', {
                type: 'button', class: 'toggle', 'aria-pressed': String(choice === ALL), text: choice,
            }));
            buttons.forEach((button, index) => button.addEventListener('click', () => {
                chosen[filter.field] = filter.choices[index];
                buttons.forEach(other => other.setAttribute('aria-pressed', String(other === button)));
                refresh();
            }));
            return el('div', { class: 'filter-group' }, el('span', { class: 'filter-caption', text: filter.caption }), ...buttons);
        }));
    }
}
