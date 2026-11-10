import {fixture} from "./fixture.js";
import {periodKey, parseCivilDate} from "./dates.js";

// Mutable page examples, reset by reloading the module/page. No repository or storage.
export const demo = structuredClone(fixture);
demo.allocations = Object.fromEntries(demo.sources.map(source =>
  [source.id, source.budget == null ? {} : {[periodKey(parseCivilDate(fixture.today))]: source.budget}]));
demo.budgets = {[periodKey(parseCivilDate(fixture.today))]: fixture.overallBudget};

export const parseAmount = text => {
  const value = Number(String(text).trim().replace(",", "."));
  return Number.isFinite(value) ? Math.round(value * 100) : 0;
};
export const amountFor = (date, id) => demo.allocations[id]?.[periodKey(date)] ?? null;
export const totalFor = date => demo.budgets[periodKey(date)] ?? null;

// Small display-only sums keep the cards consistent after a demonstration edit/delete.
export function summaryData(date) {
  const expenses = demo.expenses.filter(item => periodKey(parseCivilDate(item.date)) === periodKey(date))
    .sort((a, b) => b.date.localeCompare(a.date) || a.id.localeCompare(b.id));
  const sum = rows => rows.reduce((total, item) => total + item.amountMinor, 0);
  const groups = (items, field) => items.map(item => {
    const rows = expenses.filter(expense => expense[field] === item.id);
    return {...item, spent: sum(rows), expenseCount: rows.length};
  }).filter(item => item.expenseCount).sort((a, b) => b.spent - a.spent);
  const categories = [...demo.categories];
  if (expenses.some(item => !item.categoryId)) categories.push({id: null, name: null});
  const spent = sum(expenses), allocated = totalFor(date);
  return {
    expenses, spent, allocated, remaining: allocated - spent,
    sources: demo.sources.map(source => ({
      source, spent: sum(expenses.filter(item => item.sourceId === source.id)), limit: amountFor(date, source.id),
      remaining: amountFor(date, source.id) == null ? null : amountFor(date, source.id) - sum(expenses.filter(item => item.sourceId === source.id))
    })),
    categoryBreakdown: groups(categories, "categoryId"),
    sourceBreakdown: groups(demo.sources, "sourceId")
  };
}
