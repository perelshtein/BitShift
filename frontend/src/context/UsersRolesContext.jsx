import { createContext, useState, useEffect, useContext } from "react";
import { AdminAPIContext } from '../context/AdminAPIContext';

export const UsersRolesContext = createContext(null);

export const UsersRolesProvider = ({ children }) => {
    const [loading, setLoading] = useState(true);
    const [roles, setRoles] = useState(null);
    const [activeRole, setActiveRole] = useState(null);

    const { fetchRoles } = useContext(AdminAPIContext);

    // Fetch roles
    useEffect(() => {
        const loadData = async () => {
            setLoading(true);
            try {
                const roleData = (await fetchRoles()).data;
                setRoles(roleData);

                const activeRoleId = localStorage.getItem("activeRoleId"); 
                const updatedActiveRole = roleData.find((role) => role.id === parseInt(activeRoleId));
                setActiveRole(updatedActiveRole);
            } finally {
                setLoading(false);
            }
        };

        loadData();
    }, []);

    return (
        <UsersRolesContext.Provider value={{ roles, setRoles, activeRole, setActiveRole, loading }}>
            {children}
        </UsersRolesContext.Provider>
    );
};
