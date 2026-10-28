import {
  Archive, ArrowDown, ArrowLeft, ArrowUp, AtSign, Ban, Bell, BellOff, Bug, Camera, Check, CheckCheck, ChevronLeft, ChevronRight,
  CircleAlert, CircleCheck, CircleDashed, CircleUserRound, CircleX, Clock, Copy, Database, Download, Ellipsis, Eye,
  EyeOff, File, FileImage, FileSpreadsheet, FileText, FileX, Flag, Image, Inbox, Info, LoaderCircle, Lock, LogOut, Mail,
  MailCheck, MessageCircle, Minus, MonitorSmartphone, Pencil, Plus, QrCode, Search, ServerCog, Share, Shield, ShieldCheck,
  ShieldEllipsis, ShieldX, Smartphone, SquarePen, Timer, Trash2, UserPlus, UserRound, Users, UsersRound, WifiOff, X,
  type LucideIcon,
} from 'lucide-react-native';
import { useTheme } from '../theme/ThemeProvider';
import { ColorName } from '../theme/tokens';

/** the Lucide icons of the design, by their names there */
const icons = {
  archive: Archive, 'arrow-down': ArrowDown, 'arrow-left': ArrowLeft, 'arrow-up': ArrowUp, 'at-sign': AtSign, ban: Ban, bell: Bell,
  'bell-off': BellOff, bug: Bug, camera: Camera, check: Check, 'check-check': CheckCheck, 'chevron-left': ChevronLeft,
  'chevron-right': ChevronRight, 'circle-alert': CircleAlert, 'circle-check': CircleCheck, 'circle-dashed': CircleDashed,
  'circle-user-round': CircleUserRound, 'circle-x': CircleX, clock: Clock, copy: Copy, database: Database,
  download: Download, ellipsis: Ellipsis, eye: Eye, 'eye-off': EyeOff, file: File, 'file-image': FileImage,
  'file-spreadsheet': FileSpreadsheet, 'file-text': FileText, 'file-x': FileX, flag: Flag, image: Image, inbox: Inbox,
  info: Info, 'loader-circle': LoaderCircle, lock: Lock, 'log-out': LogOut, mail: Mail, 'mail-check': MailCheck,
  'message-circle': MessageCircle, minus: Minus, 'monitor-smartphone': MonitorSmartphone, pencil: Pencil, plus: Plus,
  'qr-code': QrCode, search: Search, 'server-cog': ServerCog, share: Share, shield: Shield, 'shield-check': ShieldCheck,
  'shield-ellipsis': ShieldEllipsis, 'shield-x': ShieldX, smartphone: Smartphone, 'square-pen': SquarePen, timer: Timer,
  'trash-2': Trash2, 'user-plus': UserPlus, 'user-round': UserRound, users: Users, 'users-round': UsersRound,
  'wifi-off': WifiOff, x: X,
} satisfies Record<string, LucideIcon>;

export type IconName = keyof typeof icons;

export function Icon({ name, size = 22, color = 'textPrimary', strokeWidth = 2 }: {
  name: IconName; size?: number; color?: ColorName; strokeWidth?: number;
}) {
  const { colors } = useTheme();
  const Component = icons[name];
  return <Component size={size} color={colors[color]} strokeWidth={strokeWidth} />;
}
