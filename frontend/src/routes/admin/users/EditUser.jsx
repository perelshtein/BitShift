import { useParams, useNavigate } from "react-router-dom";
import { useContext, useState, useEffect } from "react";
import { toast } from 'react-toastify';
import { UsersRolesContext } from "@/context/UsersRolesContext.jsx";
import { AdminAPIContext } from "@/context/AdminAPIContext.jsx";
import ComboList from "@/routes/components/ComboList";
import { ROUTES } from "@/links";
import styles from "../admin.module.css";
import { cashbackTypes } from "@/routes/Index";

export default function EditUser() {
  const { id } = useParams();    
  const { roles, loading: rolesLoading } = useContext(UsersRolesContext);
  const { sendUser, fetchUserInfo, fetchOptions } = useContext(AdminAPIContext);  

  const defaultUser = {
    name: "",
    mail: "",
    password: "",
    roleId: roles?.at(-1)?.id || 0
  };

  const [options, setOptions] = useState({});
  const [saving, setSaving] = useState(false);
  const [user, setUser] = useState(defaultUser);
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadUser = async() => {
      try {
        const [userResult, optResult] = await Promise.all([
          await fetchUserInfo({userId: id}).then(res => res.data),
          await fetchOptions().then(res => res.data)
        ]);
        setUser(userResult);
        setOptions(optResult);        
      }
      finally {
        setLoading(false);
      }
    }
    if(id) loadUser();
    else {
      // setUser({ ...defaultUser, roleId: roles.at(-1)?.id });
      setLoading(false);
    }
  }, []);

  if (loading || rolesLoading) {
    return <p>Загрузка...</p>;
  }

  if (saving) {
    return <p>Сохранение...</p>;
  }

  const handleSave = async () => {
    try {
      setSaving(true);
      const answer = await sendUser({user: user});
      toast.info(answer.message);          
      navigate(ROUTES.USERS);        
    } finally {
      setSaving(false);
    }
  };  

  const handleChange = (e) => {
      // Check if e is a synthetic event or custom object
      const name = e?.target?.name || e.name;
      const value = e?.target?.value || e.value;
      
      setUser((prev) => ({
          ...prev,
          [name]: value,
      }));
  };
  
    return (
      <>
        <Caption id={id} name={user?.name} />
        <div className={[styles.tableTwoColLayout, styles.editUser].join(' ')}>
          <label htmlFor="userName">Имя:</label>
          <input            
            id="userName"
            name="name"
            value={user?.name || ""}
            onChange={handleChange}
          />
          <label htmlFor="password">Пароль:</label>
          <input
            id="password"
            name="password"
            type="password"
            value={user?.password || ""}
            onChange={handleChange}            
          />
          <label htmlFor="mail">Почта:</label>
          <input
            id="mail"
            name="mail"
            value={user?.mail || ""}
            onChange={handleChange}
          />
          <label htmlFor="role">Роль:</label>
          <ComboList
            id="role"            
            rows={roles}
            selectedId={user.roleId}
            setSelectedId={e => setUser({ ...user, roleId: e })}
          />
          <label htmlFor="cashbackPercent">Кэшбэк:</label>
          <input
            id="cashbackPercent"
            name="cashbackPercent"
            value={user?.cashbackPercent || options.cashbackPercent}
            onChange={handleChange}
          />
          <label htmlFor="cashbackType">Тип кэшбэка:</label>
          <ComboList
            id="cashbackType"
            rows={cashbackTypes}
            selectedId={user?.cashbackType || options.cashbackType}
            setSelectedId={(e) => setUser({ ...user, cashbackType: e })}
          />

          <label htmlFor="referralPercent">Реферальный процент:</label>
          <input
            id="referralPercent"
            name="referralPercent"
            value={user?.referralPercent || options.referralPercent}
            onChange={handleChange}
          />

          <label htmlFor="referralType">Тип реф.программы:</label>
          <ComboList
            id="referralType"
            rows={cashbackTypes}
            selectedId={user?.referralType || options.referralType}
            setSelectedId={(e) => setUser({ ...user, referralType: e })}
          />
          <label>Реферал:</label>
          {user?.referralId ? <p>{user?.referralName} | {user?.referralMail}</p> : <p>Не задан</p>}
        </div>
        <button className={styles.save} onClick={handleSave}>
          Сохранить
        </button>
      </>
    );
}  

function Caption({ id, name }) {
    return id ? <h2>Редактировать пользователя {name}</h2> : <h2>Добавить пользователя</h2>;
}