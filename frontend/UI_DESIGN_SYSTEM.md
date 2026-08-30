# Employee Digital Platform UI System

## Product Structure

The product has four primary daily-work areas. The desktop sidebar is the persistent primary navigation; route permissions determine which destinations are visible. On mobile, the compact navigation retains the same order and every destination has an accessible label.

| Area | Primary page | User objective | Page structure |
| --- | --- | --- | --- |
| Workbench | Dashboard | Understand today's work and act on priorities | Page heading, operational summary, priority cards, recent knowledge, pending actions |
| Information lookup | Knowledge base and assistant | Find trusted information quickly | Context heading, search and filters, result list, source references |
| Business handling | Knowledge base detail and system management | Create, update, upload, authorize, and follow task state | Back path, key facts, filterable data table, form dialog, inline confirmation |
| Personal service | Profile, notification, role and department context | Identify the current identity and permitted scope | Persistent user identity, notification entry, visible role and department status |

## Layout Rules

- Desktop uses a persistent sidebar, 68px top bar, and a content width capped at 1480px.
- Primary pages use the same order: eyebrow, title and purpose, one primary action, filters, then content.
- Operation screens show one emphasized primary action. Edit, reset, and delete stay secondary or danger-styled.
- Data tables sit inside a single bordered surface; filters connect visually to the table without creating nested cards.
- Phone layouts stack page actions and filters, keep input text at 16px, and permit table scrolling within its own surface.

## Component Rules

- Colors are semantic: `--teal` for primary action, `--status-info` for neutral information, `--status-warning` for attention, and `--status-danger` for failed or destructive outcomes.
- Use Element Plus icons consistently. Standalone icon controls require an accessible name; icons beside text remain decorative.
- Controls have a 36px desktop minimum. Tree rows expand to 44px on phone for reliable touch interaction.
- Forms use visible labels, helper text for non-obvious fields, inline errors near the related input, and loading state on submission.
- Empty states describe the current absence and retain the next available action. Loading areas reserve their content size to avoid jumping.

## Interaction And Accessibility

- Keyboard focus uses a visible green ring. A skip link moves keyboard users directly to the main content.
- Permission-based navigation remains predictable: unavailable modules are not presented as regular actions, and the current route exposes its active state.
- Destructive actions use a confirmation dialog and danger styling. Successful, failed, and in-progress states always include text as well as color.
- Motion is limited to opacity, color, shadow, and small transforms. `prefers-reduced-motion` disables non-essential transitions.
- Long identifiers, file names, and citations wrap safely instead of forcing horizontal page scroll.
