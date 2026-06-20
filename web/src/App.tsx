import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { Role } from './auth/roles';
import AppShell from './components/AppShell';
import ComingSoonPage from './features/common/ComingSoonPage';
import ForbiddenPage from './features/common/ForbiddenPage';
import DashboardPage from './features/dashboard/DashboardPage';
import StudentDetailPage from './features/student/StudentDetailPage';
import StudentForm from './features/student/StudentForm';
import StudentListPage from './features/student/StudentListPage';
import ProtectedRoute from './routes/ProtectedRoute';
import RoleRoute from './routes/RoleRoute';

/** Ofis rolleri — öğrenci/grup/tanım ekranlarına erişebilenler. */
const OFIS = [Role.ADMIN, Role.FRONTDESK, Role.FRONTDESK_ACCOUNTING] as const;

/**
 * Giriş sonrası rol bazlı açılış yönlendirmesi (öncelik: ADMIN > FRONTDESK_ACCOUNTING >
 * FRONTDESK > TEACHER). Çoklu rolde en yetkili olana göre karar verilir.
 */
function IndexRedirect() {
  const { hasRole } = useAuth();
  if (hasRole(Role.ADMIN) || hasRole(Role.SUPER_ADMIN)) return <Navigate to="/dashboard" replace />;
  if (hasRole(Role.FRONTDESK_ACCOUNTING)) return <Navigate to="/dashboard" replace />;
  if (hasRole(Role.FRONTDESK)) return <Navigate to="/ogrenciler" replace />;
  if (hasRole(Role.TEACHER)) return <Navigate to="/yoklama" replace />;
  return <Navigate to="/dashboard" replace />;
}

export default function App() {
  return (
    <Routes>
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        }
      >
        <Route index element={<IndexRedirect />} />
        <Route path="dashboard" element={<DashboardPage />} />

        {/* Öğrenciler — gerçek sayfalar (mevcut bileşenler) */}
        <Route
          path="ogrenciler"
          element={
            <RoleRoute requiredRoles={OFIS}>
              <StudentListPage />
            </RoleRoute>
          }
        />
        <Route
          path="ogrenciler/yeni"
          element={
            <RoleRoute requiredRoles={OFIS}>
              <StudentForm />
            </RoleRoute>
          }
        />
        <Route
          path="ogrenciler/:id"
          element={
            <RoleRoute requiredRoles={OFIS}>
              <StudentDetailPage />
            </RoleRoute>
          }
        />
        <Route
          path="ogrenciler/:id/duzenle"
          element={
            <RoleRoute requiredRoles={OFIS}>
              <StudentForm />
            </RoleRoute>
          }
        />

        {/* Diğer modüller — "Yakında" placeholder (sonraki job'lar) */}
        <Route
          path="gruplar"
          element={
            <RoleRoute requiredRoles={OFIS}>
              <ComingSoonPage title="Gruplar / Kayıt" />
            </RoleRoute>
          }
        />
        <Route
          path="tanimlar"
          element={
            <RoleRoute requiredRoles={OFIS}>
              <ComingSoonPage title="Tanımlar" />
            </RoleRoute>
          }
        />
        <Route
          path="yoklama"
          element={
            <RoleRoute
              requiredRoles={[Role.ADMIN, Role.FRONTDESK, Role.FRONTDESK_ACCOUNTING, Role.TEACHER]}
            >
              <ComingSoonPage title="Yoklama" />
            </RoleRoute>
          }
        />
        <Route
          path="finans"
          element={
            <RoleRoute requiredRoles={[Role.ADMIN, Role.FRONTDESK_ACCOUNTING]}>
              <ComingSoonPage title="Finans" />
            </RoleRoute>
          }
        />
        <Route
          path="hakedis"
          element={
            <RoleRoute requiredRoles={[Role.ADMIN]}>
              <ComingSoonPage title="Hakediş" />
            </RoleRoute>
          }
        />
        <Route
          path="stok"
          element={
            <RoleRoute requiredRoles={[Role.ADMIN, Role.FRONTDESK_ACCOUNTING]}>
              <ComingSoonPage title="Stok / Satış" />
            </RoleRoute>
          }
        />
        <Route
          path="raporlar"
          element={
            <RoleRoute requiredRoles={[Role.ADMIN, Role.FRONTDESK_ACCOUNTING, Role.FRONTDESK]}>
              <ComingSoonPage title="Raporlar" />
            </RoleRoute>
          }
        />

        {/* Yetkisiz ekranı — çerçeve içinde kalır (kullanıcı menüyü görmeye devam eder) */}
        <Route path="403" element={<ForbiddenPage />} />
      </Route>

      {/* Bilinmeyen rota -> kök (oradan rol bazlı yönlendirme) */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
