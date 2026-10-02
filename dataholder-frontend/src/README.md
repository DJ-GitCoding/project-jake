# Dataholder Frontend Updates - Agreement Management

## Overview

This package adds UI components for managing agreement templates and incoming agreement requests from the Requestor Manager.

## New Pages

### 1. Agreement Templates (`/agreement-templates`)
Manage agreement templates that can be offered to requestor groups:
- Create, edit, delete templates
- Publish/unpublish templates (only published templates are visible to Requestor Manager)
- Configure access levels, terms, and rate limits

### 2. Agreement Requests (`/agreement-requests`)
Process incoming agreement requests:
- View pending, approved, testing, active, and suspended requests
- Approve or decline requests
- Run agreement tests
- Activate agreements after successful testing
- Suspend/reactivate agreements

## File Placement

```
src/
├── pages/
│   ├── AgreementTemplates.js    (NEW)
│   └── AgreementRequests.js     (NEW)
├── components/
│   └── StatusBadge.js           (NEW)
├── services/
│   └── api.js                   (ADD functions from api-additions.js)
├── App.js                       (UPDATE with new routes)
└── index.css                    (ADD styles from css-additions.css)
```

## Installation Steps

### 1. Add New Pages
Copy these files to `src/pages/`:
- `AgreementTemplates.js`
- `AgreementRequests.js`

### 2. Add New Components
Copy to `src/components/`:
- `StatusBadge.js`

### 3. Update API Service
Add the functions from `api-additions.js` to your `src/services/api.js`

### 4. Update App.js
Add the new routes as shown in `app-routes.js`:
```javascript
import AgreementTemplates from './pages/AgreementTemplates';
import AgreementRequests from './pages/AgreementRequests';

// In Routes:
<Route path="/agreement-templates" element={<AgreementTemplates />} />
<Route path="/agreement-requests" element={<AgreementRequests />} />
```

### 5. Update Navigation
Add menu items for the new pages (see `navigation-updates.js`):
- Templates (icon: IconTemplate)
- Requests (icon: IconInbox, with pending count badge)

### 6. Add CSS
Append the styles from `css-additions.css` to your `src/index.css`

### 7. Add Icons
Add these icons to your `src/components/Icons.js`:
```javascript
export const IconTemplate = () => (
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
    <line x1="3" y1="9" x2="21" y2="9"></line>
    <line x1="9" y1="21" x2="9" y2="9"></line>
  </svg>
);
```

## Features

### Agreement Templates Page
- **Stats Dashboard**: Total, published, and draft template counts
- **Template Table**: View all templates with quick publish toggle
- **Create/Edit Modal**: Full form for template configuration
  - Name and description
  - Access level (0-3)
  - Required group types
  - Query rate limits
  - Terms and conditions
  - Data usage policy
  - Manual approval requirement
  - Publish status

### Agreement Requests Page
- **Stats Dashboard**: Counts by status (pending, approved, testing, active, suspended)
- **Filter Tabs**: Quick filter by status
- **Request Table**: View all requests with inline actions
- **Detail Modal**: Full request information including:
  - Request details
  - Requestor group information
  - Purpose and description
  - Review history
  - Test results
  - Status history
- **Action Modals**: 
  - Approve/Decline with notes
  - Start and run tests
  - Activate after successful tests
  - Suspend/Reactivate agreements

## Workflow

```
1. Create Agreement Template
   └── Publish template

2. Requestor Manager discovers template
   └── Requestor initiates agreement request

3. Admin reviews request
   ├── Approve → Ready for testing
   └── Decline → Request closed

4. Start Testing
   └── Run automated tests

5. Tests Pass
   └── Activate agreement
       └── Requestor group gains access

6. Ongoing Management
   ├── Suspend if needed
   └── Reactivate when ready
```

## Build

```bash
npm run build
# or
docker-compose build dataholder-frontend
docker-compose up -d dataholder-frontend
```
