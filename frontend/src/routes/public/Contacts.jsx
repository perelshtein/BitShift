import Header from "./modules/Header";
import Footer from "./modules/Footer";
import styles from "./public.module.css";

export default function Contacts() {
    return(            
            <div className={styles.websiteContainer}>
                <div className={styles.website}>
                    <Header styles={styles} />
            
                    <main>
                      <div className={[styles.dataBar, styles.contactPage].join(" ")}>
                        <h3>Контакты</h3>

                        <h4>График работы:</h4>
                        <ul>
                            <li>Поддержка и ручные обмены с RUB:<br />
                            пн–пт: 10:00–22:00 (МСК, GMT+3).<br />
                            сб–вс: свободный график.</li>
                            <li>Автоматические обмены криптовалют: круглосуточно, 24/7.</li>
                        </ul>

                        <h4>О нашем движке:</h4>

                        Этот сервис — наша собственная разработка, созданная с нуля. Мы активно работаем над улучшениями: приводим в порядок
                        документацию и добавляем новые модули. Скоро наш движок станет доступен для установки на другие сайты!
                        Следите за анонсами на основном домене: <a href="https://bitshift.su">bitshift.su</a>.

                        <h4>Обратная связь:</h4>

                        Мы открыты к вашим идеям по улучшению сервиса! Если вы нашли ошибку, пожалуйста, <a href="mailto:e@bitshift.su">напишите нам.</a><br />
                        Для оперативного решения вопроса приложите:

                        <ul>
                            <li>Скриншоты;</li>
                            <li>Версию браузера;</li>
                            <li>Описание условий, при которых возникла ошибка.</li>
                        </ul>

                        </div>
                    </main>

                    <Footer styles={styles} />
                </div>
            </div>
    )
}