// Fictional in-memory example, imported only for visual review.
import {addSource,addCategory,createExpense,setBudget} from "./preview-model.js";
import {periodFor,civilDate} from "./budget-period.js";
const period=periodFor(new Date()),date=civilDate(period.start);
setBudget(period.start,2000000);
const cash=addSource("מזומן · Cash · Наличные"),card=addSource("אשראי · Credit card · Карта");
const food=addCategory("אוכל · Food · Еда");
const supermarket=addCategory("סופרמרקט · Supermarket · Супермаркет",food.id);
addCategory("מסעדות · Restaurants · Рестораны",food.id);
addCategory("משלוחים · Delivery · Доставка",food.id);
addCategory("בריאות · Health · Здоровье");
createExpense({amountMinor:10000,date,sourceId:cash.id,categoryId:food.id});
createExpense({amountMinor:25000,date,sourceId:card.id,categoryId:supermarket.id});
