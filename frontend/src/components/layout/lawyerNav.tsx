import type { NavTabItem } from '../ui/NavTabs'
import {
  CalendarIcon,
  CasesIcon,
  ChatIcon,
  ClientsIcon,
  DashboardIcon,
  ProfileIcon,
  SearchIcon,
  SettingsIcon,
  TeamIcon,
  TemplatesIcon,
  WorkflowsIcon,
} from './navIcons'

export interface LawyerNavSection {
  title: string
  items: NavTabItem[]
}

export const lawyerNavSections: LawyerNavSection[] = [
  {
    title: 'Рабочее место',
    items: [
      { to: '/dashboard', label: 'Дашборд', icon: <DashboardIcon /> },
      { to: '/chat', label: 'AI-чат', icon: <ChatIcon /> },
      { to: '/search', label: 'Поиск', icon: <SearchIcon /> },
      { to: '/cases', label: 'Дела', icon: <CasesIcon /> },
      { to: '/calendar', label: 'Календарь', icon: <CalendarIcon /> },
      { to: '/clients', label: 'Клиенты', icon: <ClientsIcon /> },
    ],
  },
  {
    title: 'Инструменты',
    items: [
      { to: '/templates', label: 'Шаблоны', icon: <TemplatesIcon /> },
      { to: '/workflows', label: 'Процессы', icon: <WorkflowsIcon /> },
      { to: '/team', label: 'Организация', icon: <TeamIcon /> },
    ],
  },
  {
    title: 'Аккаунт',
    items: [
      { to: '/profile', label: 'Профиль', icon: <ProfileIcon /> },
      { to: '/settings', label: 'Настройки', icon: <SettingsIcon /> },
    ],
  },
]

export const LAWYER_NAV_INDICATOR_ID = 'lawyer-nav-indicator'
