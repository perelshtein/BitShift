export const timeZones = [
    "UTC-12:00", "UTC-11:30", "UTC-11:00", "UTC-10:30", "UTC-10:00",
    "UTC-09:30", "UTC-09:00", "UTC-08:30", "UTC-08:00", "UTC-07:30",
    "UTC-07:00", "UTC-06:30", "UTC-06:00", "UTC-05:30", "UTC-05:00",
    "UTC-04:30", "UTC-04:00", "UTC-03:30", "UTC-03:00", "UTC-02:30",
    "UTC-02:00", "UTC-01:30", "UTC-01:00", "UTC-00:30", "UTC+00:00",
    "UTC+00:30", "UTC+01:00", "UTC+01:30", "UTC+02:00", "UTC+02:30",
    "UTC+03:00", "UTC+03:30", "UTC+04:00", "UTC+04:30", "UTC+05:00",
    "UTC+05:30", "UTC+05:45", "UTC+06:00", "UTC+06:30", "UTC+07:00",
    "UTC+07:30", "UTC+08:00", "UTC+08:30", "UTC+08:45", "UTC+09:00",
    "UTC+09:30", "UTC+10:00", "UTC+10:30", "UTC+11:00", "UTC+11:30",
    "UTC+12:00", "UTC+12:45", "UTC+13:00", "UTC+13:45", "UTC+14:00"
    ].map((it, index) =>
    ({id: index, name: it})
);

export function minutesToText(minutes) {
    const sign = minutes >= 0 ? "+" : "-";
    const absMinutes = Math.abs(minutes);
    const hours = Math.floor(absMinutes / 60);
    const remMinutes = absMinutes % 60;
    return `UTC${sign}${String(hours).padStart(2, "0")}:${String(remMinutes).padStart(2, "0")}`;
}

export function minutesToId(minutes) {
    let timezoneText = minutesToText(minutes);
    return timeZones.find(it => it.name === timezoneText)?.id;
}

export function idToMinutes(id) {
    let offsetText = timeZones.find(it => it.id == id)?.name;
    const match = offsetText.match(/^UTC([+-])(\d{1,2}):(\d{2})$/);
    if (!match) {
        throw new Error("Перевод UTC в минуты: Некорректный формат, ожидаем UTC±hh:mm");
    }

    const sign = match[1] === "+" ? 1 : -1;
    const hours = parseInt(match[2], 10);
    const minutes = parseInt(match[3], 10);

    return sign * (hours * 60 + minutes);
}