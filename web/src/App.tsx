import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { Role } from './auth/roles';
import AppShell from './components/AppShell';
import BranchForm from './features/branch/BranchForm';
import BranchListPage from './features/branch/BranchListPage';
import AttendancePage from './features/attendance/AttendancePage';
import ComingSoonPage from './features/common/ComingSoonPage';
import ForbiddenPage from './features/common/ForbiddenPage';
import DashboardPage from './features/dashboard/DashboardPage';
import FinancePage from './features/finance/FinancePage';
import GroupDetailPage from './features/group/GroupDetailPage';
import GroupForm from './features/group/GroupForm';
import GroupListPage from './features/group/GroupListPage';
import RoomForm from './features/room/RoomForm';
import RoomListPage from './features/room/RoomListPage';
import StudentDetailPage from './features/student/StudentDetailPage';
import StudentForm from './features/student/StudentForm';
import StudentListPage from './features/student/StudentListPage';
import TeacherForm from './features/teacher/TeacherForm';
import TeacherListPage from './features/teacher/TeacherListPage';
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

        {/* Gruplar / Kayıt — grup CRUD + grup detayında kayıt yönetimi */}
        <Route
          path="gruplar"
          element={
            <RoleRoute requiredRoles={OFIS}>
              <GroupListPage />
            </RoleRoute>
          }
        />
        <Route
          path="gruplar/yeni"
          element={
            <RoleRoute requiredRoles={OFIS}>
              <GroupForm />
            </RoleRoute>
          }
        />
        <Route
          path="gruplar/:id"
          element={
            <RoleRoute requiredRoles={OFIS}>
              <GroupDetailPage />
            </RoleRoute>
          }
        />
        <Route
          path="gruplar/:id/duzenle"
          element={
            <RoleRoute requiredRoles={OFIS}>
              <GroupForm />
            </RoleRoute>
          }
        />
        {/* Tanımlar — Branş / Salon / Öğretmen CRUD */}
        <Route
          path="branslar"
          element={
            <RoleRoute requiredRoles={OFIS}>
              <BranchListPage />
            </RoleRoute>
          }
        />
        <Route
          path="branslar/yeni"
          element={
            <RoleRoute requiredRoles={OFIS}>
              <BranchForm />
            </RoleRoute>
          }
        />
        <Route
          path="branslar/:id/duzenle"
          element={
            <RoleRoute requiredRoles={OFIS}>
              <BranchForm />
            </RoleRoute>
          }
        />
        <Route
          path="salonlar"
          element={
            <RoleRoute requiredRoles={OFIS}>
              <RoomListPage />
            </RoleRoute>
          }
        />
        <Route
          path="salonlar/yeni"
          element={
            <RoleRoute requiredRoles={OFIS}>
              <RoomForm />
            </RoleRoute>
          }
        />
        <Route
          path="salonlar/:id/duzenle"
          element={
            <RoleRoute requiredRoles={OFIS}>
              <RoomForm />
            </RoleRoute>
          }
        />
        <Route
          path="ogretmenler"
          element={
            <RoleRoute requiredRoles={OFIS}>
              <TeacherListPage />
            </RoleRoute>
          }
        />
        <Route
          path="ogretmenler/yeni"
          element={
            <RoleRoute requiredRoles={OFIS}>
              <TeacherForm />
            </RoleRoute>
          }
        />
        <Route
          path="ogretmenler/:id/duzenle"
          element={
            <RoleRoute requiredRoles={OFIS}>
              <TeacherForm />
            </RoleRoute>
          }
        />

        <Route
          path="yoklama"
          element={
            <RoleRoute
              requiredRoles={[Role.ADMIN, Role.FRONTDESK, Role.FRONTDESK_ACCOUNTING, Role.TEACHER]}
            >
              <AttendancePage />
            </RoleRoute>
          }
        />
        <Route
          path="finans"
          element={
            <RoleRoute requiredRoles={[Role.ADMIN, Role.FRONTDESK_ACCOUNTING]}>
              <FinancePage />
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
