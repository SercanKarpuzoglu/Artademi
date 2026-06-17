import { Navigate, Route, Routes } from 'react-router-dom';
import StudentDetailPage from './features/student/StudentDetailPage';
import StudentForm from './features/student/StudentForm';
import StudentListPage from './features/student/StudentListPage';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<StudentListPage />} />
      <Route path="/students/new" element={<StudentForm />} />
      <Route path="/students/:id/edit" element={<StudentForm />} />
      <Route path="/students/:id" element={<StudentDetailPage />} />
      {/* Bilinmeyen rota -> ana ekran */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
