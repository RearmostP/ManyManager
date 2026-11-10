// Fictional design examples only. Edit this file to change the default preview.
// The fixed date keeps all seven expenses visible regardless of today's date.
export const fixture = {
  today: "2026-11-25",
  overallBudget: 500000,
  categories: [
    {id: "c1", name: "Groceries"},
    {id: "c2", name: "Home, hobbies and weekend projects"},
    {id: "c3", name: "Travel"}
  ],
  sources: [
    {id: "s1", name: "Cash", budget: 80000},
    {id: "s2", name: "Credit", budget: null}
  ],
  expenses: [
    {id: "e1", categoryId: "c1", sourceId: "s1", amountMinor: 4250, date: "2026-11-11", note: ""},
    {id: "e2", categoryId: "c1", sourceId: "s2", amountMinor: 1875, date: "2026-11-13", note: "Picnic snacks"},
    {id: "e3", categoryId: "c1", sourceId: "s1", amountMinor: 12000, date: "2026-11-17", note: ""},
    {id: "e4", categoryId: "c2", sourceId: "s2", amountMinor: 24990, date: "2026-11-19", note: "Materials for an imaginary weekend project: bright paper, paint, brushes and a small wooden shelf. This deliberately long note demonstrates the two-line list preview and the full note in expense details."},
    {id: "e5", categoryId: "c2", sourceId: "s1", amountMinor: 6500, date: "2026-11-21", note: ""},
    {id: "e6", categoryId: "c3", sourceId: "s1", amountMinor: 1250, date: "2026-11-23", note: "Bus ride"},
    {id: "e7", categoryId: "c3", sourceId: "s2", amountMinor: 8800, date: "2026-11-25", note: ""}
  ]
};
