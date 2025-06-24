import { ROUTES } from '@/links';
import image from './notFound.jpg';

export default function NotFound() {
  return (
    <div className="errorPage">
      <h1>Страница не найдена</h1>
      <img src={image} alt="Tired robot in a library" />
      <p>What are you looking for?</p>
      <a href={ROUTES.WEBSITE}>&lt;&lt; Back to main page</a>
    </div>
  );
}