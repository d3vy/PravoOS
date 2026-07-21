import { useTranslation } from 'react-i18next'
import type { NavTabItem } from '../ui/NavTabs'
import {
  BillingIcon,
  CalendarIcon,
  CasesIcon,
  ChatIcon,
  ClientsIcon,
  DashboardIcon,
  InvoiceIcon,
  ProfileIcon,
  SettingsIcon,
  TeamIcon,
  TemplatesIcon,
  WorkflowsIcon,
} from './navIcons'

export interface LawyerNavSection {
  id: string
  title: string
  items: NavTabItem[]
}

export function useLawyerNavSections(): LawyerNavSection[] {
  const { t } = useTranslation()
  return [
    {
      id: 'main',
      title: t('nav.sectionMain'),
      items: [
        { to: '/dashboard', label: t('nav.dashboard'), icon: <DashboardIcon /> },
        { to: '/chat', label: t('nav.chat'), icon: <ChatIcon /> },
        { to: '/cases', label: t('nav.cases'), icon: <CasesIcon /> },
        { to: '/clients', label: t('nav.clients'), icon: <ClientsIcon /> },
        { to: '/calendar', label: t('nav.calendar'), icon: <CalendarIcon /> },
        { to: '/invoices', label: t('nav.invoices'), icon: <InvoiceIcon /> },
      ],
    },
    {
      id: 'tools',
      title: t('nav.sectionTools'),
      items: [
        { to: '/templates', label: t('nav.templates'), icon: <TemplatesIcon /> },
        { to: '/workflows', label: t('nav.workflows'), icon: <WorkflowsIcon /> },
      ],
    },
  ]
}

export function useLawyerAccountLinks(): NavTabItem[] {
  const { t } = useTranslation()
  return [
    { to: '/profile', label: t('nav.profile'), icon: <ProfileIcon /> },
    { to: '/team', label: t('nav.organization'), icon: <TeamIcon /> },
    { to: '/billing', label: t('nav.billing'), icon: <BillingIcon /> },
    { to: '/settings', label: t('nav.settings'), icon: <SettingsIcon /> },
  ]
}

export const LAWYER_NAV_INDICATOR_ID = 'lawyer-nav-indicator'
