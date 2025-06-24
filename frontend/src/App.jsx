import {
  createBrowserRouter,
  RouterProvider,
  Outlet
} from "react-router-dom";
import { createContext, lazy, Suspense } from 'react';
import { ToastContainer, toast } from 'react-toastify';
import CriticalError from "./routes/components/error/CriticalError/CriticalError";
import NotFound from "./routes/components/error/NotFound/NotFound";
import { ROUTES } from "./links.js";
import "./index.css";
import 'react-toastify/dist/ReactToastify.css';
import MaintenanceWrapper from "./routes/components/Maintenance";

// Провайдеры
import { AuthProvider } from './context/AuthContext.jsx';
import { AdminAPIProvider } from './context/AdminAPIContext.jsx';
import { CommonAPIProvider } from './context/CommonAPIContext.jsx';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { UsersRolesProvider } from "./context/UsersRolesContext.jsx";
import { CurrencyProvider } from "./context/CurrencyContext.jsx";
import { WebsiteAPIProvider } from "./context/WebsiteAPIContext.jsx";
import AnalyticsAndChat from "./routes/AnalyticsAndChat";

export const AdditionalCurrencyContext = createContext([
  { id: 1, name: 'RUB', currencyCode: 'RUB' },  
  { id: 3, name: 'USDT', currencyCode: 'USDT' }
  ])

const Layout = () => (
  <>
    <ToastContainer position="top-center" />  
    <Outlet />  
  </>
);

// У нас 3 сборки (bundles):
// сайт, админка и shared lib
const Menus = lazy(() => import("./routes/admin/Menus"));
const Currency = lazy(() => import("./routes/admin/currency/Currency"));
const EditCurrency = lazy(() => import("./routes/admin/currency/EditCurrency"));
const CurrencyFields = lazy(() => import("./routes/admin/currency-fields/CurrencyFields"));
const EditCurrencyField = lazy(() => import("./routes/admin/currency-fields/EditCurrencyField"));
const Direction = lazy(() => import("./routes/admin/direction/Direction"));
const EditDirection = lazy(() => import("./routes/admin/direction/EditDirection"));
const Course = lazy(() => import("./routes/admin/course/Course"));
const Order = lazy(() => import("./routes/admin/orders/Orders"));
const BonusUsers = lazy(() => import("./routes/admin/bonus-users/BonusUsers"));
const BonusReferrals = lazy(() => import("./routes/admin/bonus-referrals/BonusReferrals"));
const EditOrder = lazy(() => import("./routes/admin/orders/EditOrder"));
const OrderLog = lazy(() => import("./routes/admin/orders/OrderLog"));
const DirectionAddGroup = lazy(() => import("./routes/admin/direction/DirectionAddGroup"));
const NotifyAdmin = lazy(() => import("./routes/admin/notify/NotifyAdmin"));
const NotifyUser = lazy(() => import("./routes/admin/notify/NotifyUser"));
const Options = lazy(() => import("./routes/admin/options/Options"));
const EditTimetable = lazy(() => import("./routes/admin/options/EditTimetable"));
const Users = lazy(() => import("./routes/admin/users/Users"));
const EditUser = lazy(() => import("./routes/admin/users/EditUser"));
const Roles = lazy(() => import("./routes/admin/users/Roles"));
const EditRole = lazy(() => import("./routes/admin/users/EditRole"));
const Reviews = lazy(() => import("./routes/admin/reviews/Reviews"));
const EditReview = lazy(() => import("./routes/admin/reviews/EditReview"));
const News = lazy(() => import("./routes/admin/news/News"));
const EditNews = lazy(() => import("./routes/admin/news/EditNews"));
const Login = lazy(() => import("./routes/components/Login"));
const Logout = lazy(() => import("./routes/components/Logout"));
const OptionsCoinMarketCap = lazy(() => import("./routes/admin/course/CoinMarketCap"));
const OptionsBybit = lazy(() => import("./routes/admin/course/Bybit"));
const OptionsGarantex = lazy(() => import("./routes/admin/course/Garantex"));
const OptionsBinance = lazy(() => import("./routes/admin/course/Binance"));
const OptionsCbr = lazy(() => import("./routes/admin/course/Cbr"));
const OptionsMexc = lazy(() => import("./routes/admin/course/Mexc"));
const Formula = lazy(() => import("./routes/admin/formulas/Formula"));
const EditFormula = lazy(() => import("./routes/admin/formulas/EditFormula"));
const OrderStatus = lazy(() => import("./routes/admin/order-status/OrderStatus"));
const EditOrderStatus = lazy(() => import("./routes/admin/order-status/EditOrderStatus"));
const Profile = lazy(() => import("./routes/public/user/Profile"));
const Rules = lazy(() => import("./routes/public/Rules"));
const StopWords = lazy(() => import("./routes/admin/reviews/StopWords"));
const BonusUserOrders = lazy(() => import("./routes/admin/bonus-users/BonusUserOrders"));
const BonusReferralOrders = lazy(() => import("./routes/admin/bonus-referrals/BonusReferralOrders"));


// Сайт
const Index = lazy(() => import("./routes/public/Index"));
const CheckupOrder = lazy(() => import("./routes/public/CheckupOrder"));
const MyOrders = lazy(() => import("./routes/public/user/orders/MyOrders"));
const OrderById = lazy(() => import("./routes/public/user/OrderById"));
const Contacts = lazy(() => import("./routes/public/Contacts"));
const WebsiteNews = lazy(() => import("./routes/public/news/WebsiteNews"));
const WebsiteNewsById = lazy(() => import("./routes/public/news/WebsiteNewsById"));
const MyReviews = lazy(() => import('./routes/public/user/reviews/MyReviews'));
const MyReviewsById = lazy(() => import('./routes/public/user/reviews/MyReviewsById'));
const WebsiteReviews = lazy(() => import('./routes/public/reviews/WebsiteReviews'));
const MyReferrals = lazy(() => import('./routes/public/user/referrals/Referrals'));
const Cashback = lazy(() => import('./routes/public/user/cashback/Cashback'));
  
function App(){
  const routes = [
    {
      path: ROUTES.ADMIN,
      element: (        
        <Menus />
      ),
      errorElement: <CriticalError />,       
      children: [
        {
          path: ROUTES.CURRENCIES,
          element: <Currency />
        },
        {
          path: ROUTES.EDIT_CURRENCY,
          element: <EditCurrency />
        },
        {
          path: `${ROUTES.EDIT_CURRENCY}/:id`,
          element: <EditCurrency />
        },
        {
          path: ROUTES.CURRENCY_FIELDS,
          element: <CurrencyFields />
        },
        {
          path: ROUTES.EDIT_CURRENCY_FIELD,
          element: <EditCurrencyField />
        },
        {
          path: `${ROUTES.EDIT_CURRENCY_FIELD}/:id`,
          element: <EditCurrencyField />
        },
        {
          path: ROUTES.DIRECTIONS,
          element: <Direction />
        },
        {
          path: ROUTES.EDIT_DIRECTION,
          element: <EditDirection />
        },
        {
          path: `${ROUTES.EDIT_DIRECTION}/:id`,
          element: <EditDirection />
        },
        {
          path: ROUTES.DIRECTION_ADD_GROUP,
          element: <DirectionAddGroup />
        },
        {
          path: ROUTES.FORMULAS,
          element: <Formula />
        },
        {
          path: ROUTES.EDIT_FORMULA,
          element: <EditFormula />
        },
        {
          path: `${ROUTES.EDIT_FORMULA}/:id`,
          element: <EditFormula />
        },
        {
          path: ROUTES.COURSES,
          element: <Course />
        },
        {
          path: ROUTES.ORDERS,
          element: <Order />
        },
        {
          path: ROUTES.BONUS_USERS,
          element: <BonusUsers />
        },
        {
          path: ROUTES.BONUS_REFERRALS,
          element: <BonusReferrals />
        },
        {
          path: `${ROUTES.EDIT_ORDER}/:id`,
          element: <EditOrder />
        },
        {
          path: `${ROUTES.ORDER_LOG}/:id`,
          element: <OrderLog />
        },
        {
          path: ROUTES.NOTIFY_ADMIN,
          element: <NotifyAdmin />
        },
        {
          path: ROUTES.NOTIFY_USER,
          element: <NotifyUser />
        },
        {
          path: ROUTES.OPTIONS,
          element: <Options />
        },
        {
          path: ROUTES.EDIT_TIMETABLE,
          element: <EditTimetable />
        },
        {
          path: `${ROUTES.EDIT_TIMETABLE}/:id`,
          element: <EditTimetable />
        },
        {
          path: ROUTES.USERS,
          element: <Users />
        },
        {
          path: ROUTES.EDIT_USER,
          element: <EditUser />
        },
        {
          path: `${ROUTES.EDIT_USER}/:id?`,
          element: <EditUser />
        },
        {
          path: ROUTES.ROLES,
          element: <Roles />
        },
        {
          path: ROUTES.EDIT_ROLE,
          element: <EditRole />
        },
        {
          path: `${ROUTES.EDIT_ROLE}/:id`,
          element: <EditRole />
        },
        {
          path: ROUTES.REVIEWS,
          element: <Reviews />
        },
        {
          path: `${ROUTES.EDIT_REVIEW}/:id`,
          element: <EditReview />
        },
        {
          path: ROUTES.NEWS,
          element: <News />
        },
        {
          path: ROUTES.EDIT_NEWS,
          element: <EditNews />
        },
        {
          path: `${ROUTES.EDIT_NEWS}/:id`,
          element: <EditNews />
        },
        {
          path: ROUTES.OPTIONS_GARANTEX,
          element: <OptionsGarantex />
        },
        {
          path: ROUTES.OPTIONS_BYBIT,
          element: <OptionsBybit />
        },
        {
          path: ROUTES.OPTIONS_BINANCE,
          element: <OptionsBinance />
        },
        {
          path: ROUTES.OPTIONS_COINMARKETCAP,
          element: <OptionsCoinMarketCap />
        },
        {
          path: ROUTES.OPTIONS_CBR,
          element: <OptionsCbr />
        },
        {
          path: ROUTES.OPTIONS_MEXC,
          element: <OptionsMexc />
        },
        {
          path: ROUTES.ORDER_STATUS,
          element: <OrderStatus />
        },
        {
          path: ROUTES.EDIT_ORDER_STATUS,
          element: <EditOrderStatus />
        },
        {
          path: `${ROUTES.EDIT_ORDER_STATUS}/:id`,
          element: <EditOrderStatus />
        },
        {
          path: ROUTES.STOP_WORDS,
          element: <StopWords />
        },
        {
          path: ROUTES.BONUS_USER_ORDERS,
          element: <BonusUserOrders />
        },
        {
          path: ROUTES.BONUS_REFERRAL_ORDERS,
          element: <BonusReferralOrders />
        }        
      ]     
    }    
  ]

  const publicRoutes = [
    { path: ROUTES.LOGIN, element: <Login /> },
        { path: ROUTES.LOGOUT, element: <Logout /> },
        { path: ROUTES.WEBSITE, element: <Index /> },
        { path: ROUTES.CHECKUP_ORDER, element: <CheckupOrder /> },
        { path: ROUTES.MY_ORDERS, element: <MyOrders /> },
        { path: `${ROUTES.ORDER_BY_ID}/:id`, element: <OrderById /> },
        { path: ROUTES.ACCOUNT, element: <Profile /> },      
        { path: ROUTES.RULES, element: <Rules /> },
        { path: ROUTES.CONTACTS, element: <Contacts /> },
        { path: ROUTES.WEBSITE_NEWS, element: <WebsiteNews />},
        { path: `${ROUTES.WEBSITE_NEWS}/:id`, element: <WebsiteNewsById /> },
        { path: ROUTES.MY_REVIEWS, element: <MyReviews /> },
        { path: ROUTES.MY_REVIEW_BY_ID, element: <MyReviewsById /> },
        { path: `${ROUTES.MY_REVIEW_BY_ID}/:id`, element: <MyReviewsById /> },
        { path: ROUTES.WEBSITE_REVIEWS, element: <WebsiteReviews /> },
        { path: ROUTES.REFERRALS, element: <MyReferrals /> },
        { path: ROUTES.CASHBACK, element: <Cashback /> }
  ]

  const protectedRoutes = wrapWithProtectedRoute(routes);
  const websiteRoutes = wrapWithMaintenance(wrapWithGoogleAnalytics(publicRoutes));

  const router = createBrowserRouter([
    { element: <Layout />, children: [
        ...protectedRoutes,
        ...websiteRoutes,
        {
          path: '*',
          element: <NotFound />,
        }
      ] },
  ]);

  return (    
    <AuthProvider>        
      <CommonAPIProvider>          
        <WebsiteAPIProvider>
          <CurrencyProvider>              
            <AdminAPIProvider>      
              <Suspense fallback={<div>Загрузка сборки (bundle)...</div>}>    
                <RouterProvider router={router} />   
              </Suspense>
            </AdminAPIProvider>        
          </CurrencyProvider>
        </WebsiteAPIProvider>          
      </CommonAPIProvider>        
    </AuthProvider>
  );
}

function wrapWithProtectedRoute(routes) {  
  return routes.map(route => ({
    ...route,
    element: <ProtectedRoute><UsersRolesProvider>{route.element}</UsersRolesProvider></ProtectedRoute>,
    children: route.children ? wrapWithProtectedRoute(route.children) : undefined,
  }));
}

function wrapWithGoogleAnalytics(routes) {  
  return routes.map(route => ({
    ...route,
    element: <AnalyticsAndChat>{route.element}</AnalyticsAndChat>,    
    children: route.children ? wrapWithGoogleAnalytics(route.children) : undefined,
  }));
}

function wrapWithMaintenance(routes) {
    return routes.map(route => ({
        ...route,
        element: <MaintenanceWrapper>{route.element}</MaintenanceWrapper>,
        children: route.children ? wrapWithMaintenance(route.children) : undefined,
    }));
}

export default App;
