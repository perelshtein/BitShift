import { useState, useEffect } from "react";

const Clock = ({ serverTimezoneOffset }) => {
  const [time, setTime] = useState(() => {
    const now = new Date();
    const browserOffset = now.getTimezoneOffset(); // In minutes
    now.setMinutes(now.getMinutes() + browserOffset + serverTimezoneOffset); // Adjust for timezone
    return now;
  });

  useEffect(() => {
    const timerId = setInterval(() => {
      const now = new Date();
      const browserOffset = now.getTimezoneOffset(); // In minutes
      now.setMinutes(now.getMinutes() + browserOffset + serverTimezoneOffset); // Adjust for timezone
      setTime(now);
    }, 1000);

    return () => clearInterval(timerId); // Cleanup on unmount
  }, [serverTimezoneOffset]);

  return <>{time.toLocaleTimeString()}</>;
};

export default Clock;