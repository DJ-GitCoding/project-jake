/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router';
// Styles
import './styles/main.scss';
// Context Providers
import { AuthProvider } from './context/AuthContext';
import { AlertProvider } from './context/AlertContext';
// i18n
import { I18nProvider } from './i18n';
// Components
import Layout from './components/Layout';
import ProtectedRoute from './components/ProtectedRoute';
import ToastContainer from './components/ToastContainer';
import AlertModal from './components/AlertModal';
import ConfirmModal from './components/ConfirmModal';
import Footer from './components/Footer';
// Pages
import Login from './pages/Login';
import ForgotPassword from './pages/ForgotPassword';
import ResetPassword from './pages/ResetPassword';
import Dashboard from './pages/Dashboard';
import RequestorGroups from './pages/RequestorGroups';
import Users from './pages/Users';
import DataHolderGroups from './pages/DataHolderGroups';
import Subscriptions from './pages/Subscriptions';
import Unauthorized from './pages/Unauthorized';
import NotFound from './pages/NotFound';
// Role constants
import { UserRoles } from './context/AuthContext';

function App() {
  return (
    <I18nProvider>
      <BrowserRouter>
      <AuthProvider>
        <AlertProvider>
          {/* Global Alert Components */}
          <ToastContainer />
          <AlertModal />
          <ConfirmModal />
          <Routes>
            {/* Public Routes */}
            <Route path="/login" element={<Login />} />
            <Route path="/forgot-password" element={<ForgotPassword />} />
            <Route path="/reset-password" element={<ResetPassword />} />
            <Route path="/unauthorized" element={<Unauthorized />} />
            {/* Protected Routes */}
            <Route
              element={
                <ProtectedRoute>
                  <Layout />
                </ProtectedRoute>
              }
            >
              {/* Dashboard - All authenticated users */}
              <Route path="/dashboard" element={<Dashboard />} />
              {/* Subscriptions - All authenticated users can view */}
              <Route path="/subscriptions" element={<Subscriptions />} />
              {/* Requestor Groups - All authenticated users */}
              <Route path="/requestor-groups" element={<RequestorGroups />} />
              {/* Data Holders - All authenticated users can view, but actions are role-restricted */}
              <Route path="/data-holder-groups" element={<DataHolderGroups />} />
              {/* Users - requestor_group_admin and above */}
              <Route
                path="/users"
                element={
                  <ProtectedRoute
                    allowedRoles={[
                      UserRoles.JADDAR_MASTER_ADMIN,
                      UserRoles.GROUP_ADMIN,
                      UserRoles.REQUESTOR_GROUP_ADMIN,
                    ]}
                  >
                    <Users />
                  </ProtectedRoute>
                }
              />
            </Route>
            {/* Redirect root to dashboard */}
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            {/* 404 */}
            <Route path="*" element={<NotFound />} />
          </Routes>
          <Footer />
        </AlertProvider>
      </AuthProvider>
      </BrowserRouter>
    </I18nProvider>
  );
}

export default App;