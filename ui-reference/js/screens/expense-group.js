import {state} from "../state.js";
import {snapshotFor} from "../preview-model.js";
import {parseCivilDate} from "../budget-period.js";
import {tr,escapeValue,periodDescription} from "../render-helpers.js";
import {expenseRows} from "./summary.js";

export function expenseGroup(){
 const group=state.expenseGroup;
 const date=parseCivilDate(group.periodStart);
 const snapshot=snapshotFor(date);
 const rows=snapshot.expenses.filter(item=>group.category?item.categoryId===group.id:item.sourceId===group.id);
 return `<section class="summary fade-in"><button class="chip secondary-action" data-action="back">${tr("back")}</button>
 <h1 style="margin-top:20px;overflow-wrap:anywhere">${escapeValue(group.name)}</h1>
 <p class="description" style="margin-top:12px">${escapeValue(periodDescription(state.language,date))}</p>
 <div data-expense-group-rows>${expenseRows(rows)||`<p class="empty">${tr("group_expenses_empty")}</p>`}</div></section>`;
}
