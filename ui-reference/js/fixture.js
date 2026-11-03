// Fictional in-memory example, imported only for visual review.
import {addSource,createExpense,setBudget} from "./preview-model.js";
import {periodFor,civilDate} from "./budget-period.js";
const period=periodFor(new Date()),date=civilDate(period.start);
setBudget(period.start,2000000);
const cash=addSource("מזומן · Cash · Наличные"),card=addSource("אשראי · Credit card · Карта");
createExpense({amountMinor:10000,date,sourceId:cash.id});
createExpense({amountMinor:25000,date,sourceId:card.id});
