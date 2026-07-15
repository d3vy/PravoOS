import type { NavTabItem } from '../ui/NavTabs'
import {
  BillingIcon,
  CalendarIcon,
  CasesIcon,
  ChatIcon,
  ClientsIcon,
  DashboardIcon,
  InvoiceIcon,
  MessagesIcon,
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
      { to: '/messages', label: 'Сообщения', icon: <MessagesIcon /> },
      { to: '/calendar', label: 'Календарь', icon: <CalendarIcon /> },
      { to: '/clients', label: 'Клиенты', icon: <ClientsIcon /> },
      { to: '/invoices', label: 'Счета', icon: <InvoiceIcon /> },
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
      { to: '/billing', label: 'Подписка', icon: <BillingIcon /> },
      { to: '/settings', label: 'Настройки', icon: <SettingsIcon /> },
    ],
  },
]

export const LAWYER_NAV_INDICATOR_ID = 'lawyer-nav-indicator'
