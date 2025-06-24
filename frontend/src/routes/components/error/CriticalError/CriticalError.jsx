import { useRouteError } from "react-router-dom";
import image from './criticalError.jpg';

export default function CriticalError() {
  const error = useRouteError();
  console.error(error);

  return (
    <div className="errorPage">
      <h1>Критическая ошибка</h1>
      <img src={image} alt="Broken robot" />
      <p>Something went wrong..</p>
      <p>
        <i>{error.statusText || error.message}</i>
      </p>
    </div>
  );
}
