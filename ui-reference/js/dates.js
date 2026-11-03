// Date formatting/navigation for the fixed demonstration period, not an accounting engine.
export const civilDate = date =>
  [date.getFullYear(), String(date.getMonth() + 1).padStart(2, "0"), String(date.getDate()).padStart(2, "0")].join("-");
export const parseCivilDate = text => new Date(text + "T12:00:00");
export function periodFor(date) {
  const start = new Date(date.getFullYear(), date.getMonth() - (date.getDate() < 10 ? 1 : 0), 10);
  return {start, endExclusive: new Date(start.getFullYear(), start.getMonth() + 1, 10)};
}
export const periodKey = date => civilDate(periodFor(date).start);
export const adjacentPeriod = (date, direction) => {
  const {start} = periodFor(date);
  return new Date(start.getFullYear(), start.getMonth() + direction, 10);
};
export const lastDate = period => new Date(period.endExclusive.getFullYear(), period.endExclusive.getMonth(), 9);
