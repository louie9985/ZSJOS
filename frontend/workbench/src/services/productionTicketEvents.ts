// Refresh mounted consumers after the global assignment dialog changes a ticket.
export const TICKET_CHANGED = 'zsjos:production-ticket-changed'
export const notifyTicketChanged = () => window.dispatchEvent(new Event(TICKET_CHANGED))
