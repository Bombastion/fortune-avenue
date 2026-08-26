import { NavLink } from "react-router-dom";

const links = [
  { to: "/", label: "Home", end: true },
  { to: "/users", label: "Users" },
  { to: "/boards", label: "Boards" },
  { to: "/boards/new", label: "New board" },
  { to: "/games", label: "Games" },
];

export function NavBar() {
  return (
    <nav className="nav">
      <span className="nav__brand">Fortune Avenue</span>
      <div className="nav__links">
        {links.map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            end={link.end}
            className={({ isActive }) => `nav__link${isActive ? " nav__link--active" : ""}`}
          >
            {link.label}
          </NavLink>
        ))}
      </div>
    </nav>
  );
}
