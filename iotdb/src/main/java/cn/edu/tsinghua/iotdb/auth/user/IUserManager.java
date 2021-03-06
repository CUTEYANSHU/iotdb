package cn.edu.tsinghua.iotdb.auth.user;

import cn.edu.tsinghua.iotdb.auth.AuthException;
import cn.edu.tsinghua.iotdb.auth.entity.User;

import java.util.List;

/**
 * This interface provides accesses to users.
 */
public interface IUserManager {

    /**
     * Get a user object.
     * @param username The name of the user.
     * @return A user object whose name is username or null if such user does not exist.
     * @throws AuthException
     */
    User getUser(String username) throws AuthException;

    /**
     * Create a user with given username and password. New users will only be granted no privileges.
     *
     * @param username is not null or empty
     * @param password is not null or empty
     * @return True if the user is successfully created, false when the user already exists.
     * @throws AuthException if the given username or password is illegal.
     */
    boolean createUser(String username, String password) throws AuthException;

    /**
     * Delete a user.
     *
     * @param username the username of the user.
     * @return True if the user is successfully deleted, false if the user does not exists.
     * @throws AuthException .
     */
    boolean deleteUser(String username) throws AuthException;

    /**
     * Grant a privilege on a path to a user.
     *
     * @param username The username of the user to which the privilege should be added.
     * @param path  The path on which the privilege takes effect. If the privilege is a path-free privilege, this should be "root".
     * @param privilegeId An integer that represents a privilege.
     * @return True if the permission is successfully added, false if the permission already exists.
     * @throws AuthException If the user does not exist or the privilege or the path is illegal.
     */
    boolean grantPrivilegeToUser(String username, String path, int privilegeId) throws AuthException;

    /**
     * Revoke a privilege on path from a user.
     *
     * @param username The username of the user from which the privilege should be removed.
     * @param path The path on which the privilege takes effect. If the privilege is a path-free privilege, this should be "root".
     * @param privilegeId An integer that represents a privilege.
     * @return True if the permission is successfully revoked, false if the permission does not exists.
     * @throws AuthException If the user does not exist or the privilege or the path is illegal.
     */
    boolean revokePrivilegeFromUser(String username, String path, int privilegeId) throws AuthException;

    /**
     * Modify the password of a user.
     *
     * @param username The user whose password is to be modified.
     * @param newPassword The new password.
     * @return True if the password is successfully modified, false if the new password is illegal.
     * @throws AuthException If the user does not exists.
     */
    boolean updateUserPassword(String username, String newPassword) throws AuthException;


    /**
     * Add a role to a user.
     *
     * @param roleName The name of the role to be added.
     * @param username The name of the user to which the role is added.
     * @return True if the role is successfully added, false if the role already exists.
     * @throws AuthException If the user does not exist.
     */
    boolean grantRoleToUser(String roleName, String username) throws AuthException;

    /**
     * Revoke a role from a user.
     *
     * @param roleName The name of the role to be removed.
     * @param username The name of the user from which the role is removed.
     * @return True if the role is successfully removed, false if the role does not exist.
     * @throws AuthException If the user does not exist.
     */
    boolean revokeRoleFromUser(String roleName, String username) throws AuthException;

    /**
     * Re-initialize this object.
     */
    void reset() throws AuthException;

    /**
     *
     * @return A list that contains all users'name.
     */
    List<String> listAllUsers();
}
