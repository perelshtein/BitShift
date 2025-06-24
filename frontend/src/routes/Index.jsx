import styles from "@/routes/public/public.module.css";

export default function Index() {
    return (
      <p>
        This is a demo for React Router.
        <br />
        Check out{" "}
        <a href="https://reactrouter.com">
          the docs at reactrouter.com
        </a>
        .
      </p>
    );
  }

  export const orderStatusList = [
    { id: "new", name: "Новая" },
    { id: "waitingForPayment", name: "Ожидание оплаты" },
    { id: "waitingForConfirmation", name: "Ждем подтверждения оплаты" },
    { id: "payed", name: "Оплаченная" },
    { id: "waitingForPayout", name: "Ждем подтверждения выплаты" },
    { id: "onCheck", name: "На проверке" },
    { id: "completed", name: "Выполнено" },
    { id: "cancelled", name: "Отмененная" },
    { id: "error", name: "Ошибка" },
    { id: "deleted", name: "Удаленная" },
    { id: "cancelledUnprofitable", name: "Отменена системой (курс изменился)" }
  ]

  export const orderSrcList = [
    { id: "admin_panel", name: "Админка" },
    { id: "autopay", name: "Модуль автовыплат" },
    { id: "user", name: "Пользователь" }
  ]

  export const reviewStatusList = [
    { id: "approved", name: "Опубликован" },
    { id: "moderation", name: "На модерации" },
    { id: "banned", name: "Забанен" },
    { id: "deleted", name: "Удален" }  
  ]

  export const cashbackTypes = [
    { id: "FROM_SUM", name: "от суммы обмена" },
    { id: "FROM_PROFIT", name: "от прибыли обменника" }
  ]

  export const payoutTypes = [
    { id: "pending", name: "в работе" },
    { id: "finished", name: "выплачено" }
  ]

export const omit = (obj, keyToOmit) => {
  const { [keyToOmit]: _, ...rest } = obj;
  return rest;
};

export function Warning({text}) {
  return(
    <div className={styles.warningBlock}>
      <img src="/warning-icon.svg" style={{height: "1.5em"}} />
      {text}
    </div>
  )
}