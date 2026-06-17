import { Navigate, Route, Routes } from 'react-router-dom';
import StudentListPage from './features/student/StudentListPage';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<StudentListPage />} />
      {/* Bilinmeyen rota -> ana ekran */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
