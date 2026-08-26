import { Route, BrowserRouter, Routes } from "react-router-dom";
import { NavBar } from "./components/NavBar";
import { HomePage } from "./pages/HomePage";
import { UsersPage } from "./pages/UsersPage";
import { BoardsListPage } from "./pages/BoardsListPage";
import { BoardCreatePage } from "./pages/BoardCreatePage";
import { BoardDetailPage } from "./pages/BoardDetailPage";
import { GamesPage } from "./pages/GamesPage";
import { GameDetailPage } from "./pages/GameDetailPage";

function App() {
  return (
    <BrowserRouter>
      <NavBar />
      <main className="app">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/users" element={<UsersPage />} />
          <Route path="/boards" element={<BoardsListPage />} />
          <Route path="/boards/new" element={<BoardCreatePage />} />
          <Route path="/boards/:id" element={<BoardDetailPage />} />
          <Route path="/games" element={<GamesPage />} />
          <Route path="/games/:id" element={<GameDetailPage />} />
        </Routes>
      </main>
    </BrowserRouter>
  );
}

export default App;
