"use client";
import { useRouter } from "next/navigation";
import { LogOut } from "lucide-react";
export function LogoutButton(){const router=useRouter();async function logout(){await fetch("/api/session/logout",{method:"POST"});router.replace("/login");router.refresh();}return <button onClick={logout} className="nav-link w-full"><LogOut size={16}/>Sign out</button>;}
