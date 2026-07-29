package com.stockmanager.util;

import com.stockmanager.model.User;

/**
 * Holds the currently logged-in user for the duration of the session.
 */
public class Session {
    private static User currentUser;

    public static User getUser()           { return currentUser; }
    public static void setUser(User user)  { currentUser = user; }
    public static boolean isAdmin()        { return currentUser != null && currentUser.isAdmin(); }
    public static void clear()             { currentUser = null; }

    /** Returns the shop ID this session is locked to.
     *  For admins this may be overridden by the shop selector. */
    public static Integer getLockedShopId() {
        if (currentUser == null) return null;
        return currentUser.isAdmin() ? null : currentUser.getShopId();
    }
}
