import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
const SERVER_ROOT = import.meta.env.VITE_SERVER_ROOT;
const GA_ID = import.meta.env.VITE_GA_ID;
const TAWK_ID = import.meta.env.VITE_TAWK_ID;

// Helper function to send page view
const sendPageView = (path) => {
  if (window.gtag) {
    window.gtag('event', 'page_view', {
      page_path: path,
    //   page_title: document.title, // Optional
    });
  }
};

const addGAScript = () => {
  if (import.meta.env.MODE === 'production') {
    const ga = document.createElement('script');
    ga.src = `${SERVER_ROOT}/gtag/js?id=${GA_ID}`;
    ga.async = true;
    document.head.appendChild(ga);

    window.dataLayer = window.dataLayer || [];
    window.gtag = window.gtag || function () { window.dataLayer.push(arguments); };
    window.gtag('js', new Date());
    window.gtag('config', GA_ID);
  }
}

const addChat = () => {
  if (document.getElementById('tawk-script-97654')) return; // prevent duplicate in dev mode

  const Tawk_LoadStart=new Date();
  const tawk = document.createElement('script');
  tawk.src = `https://embed.tawk.to/${TAWK_ID}`;
  tawk.async = true;
  tawk.charset = 'UTF-8';
  tawk.setAttribute('crossorigin', '*');
  tawk.id = 'tawk-script-97654';
  document.head.appendChild(tawk);
}

// Component to track route changes
export default function AnalyticsAndChat({children}) {
  const location = useLocation();

  useEffect(() => {
    GA_ID && addGAScript();
    TAWK_ID && addChat();
  }, []);

  useEffect(() => {
    sendPageView(location.pathname + location.search);
  }, [location]);

  return children;
}