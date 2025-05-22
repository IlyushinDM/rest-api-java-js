const ApiService = {
    // ... (существующий метод request) ...
    async request(endpoint, method = 'GET', data = null, requiresAuth = true) {
        const url = `${clientConfig.serverUrl}${endpoint}`;
        const headers = {
            'Content-Type': 'application/json',
        };

        if (requiresAuth) {
            const token = Auth.getToken();
            if (token) {
                headers['Authorization'] = `Bearer ${token}`;
            } else if (method !== 'GET' && !endpoint.includes('/auth/login') && !(endpoint ==='/students' && method === 'POST') ) { // Уточнено для регистрации
                console.warn(`Attempting to make an authenticated ${method} request to ${endpoint} without a token.`);
                Auth.logout();
                AppRouter.navigateTo('/login');
                throw new Error('Authentication required and token is missing.');
            }
        }

        const config = {
            method: method,
            headers: headers,
        };

        if (data && (method === 'POST' || method === 'PUT')) {
            config.body = JSON.stringify(data);
        }

        try {
            const response = await fetch(url, config);
            if (response.status === 204) {
                return null;
            }

            let responseData;
            const contentType = response.headers.get("content-type");
            if (contentType && contentType.includes("application/json")) {
                responseData = await response.json();
            } else {
                const textResponse = await response.text();
                responseData = textResponse ? { message: textResponse } : null;
            }

            if (!response.ok) {
                const errorMessage = (responseData && responseData.error) ? responseData.error :
                                     (responseData && responseData.message) ? responseData.message :
                                     `Request failed with status ${response.status}`;
                console.error(`API Error ${response.status}: ${errorMessage} for ${method} ${url}`);

                if (response.status === 401 && requiresAuth && endpoint !== '/auth/login') {
                    Auth.logout();
                    AppRouter.navigateTo('/login');
                }
                throw new Error(errorMessage);
            }
            return responseData;
        } catch (error) {
            console.error(`ApiService Error during ${method} ${url}:`, error.message, error);
            throw error;
        }
    },


    // === Аутентификация ===
    login: (credentials) => ApiService.request('/auth/login', 'POST', credentials, false),
    logout: () => {
        console.log("Client-side logout initiated.");
        return Promise.resolve({ message: "Logged out on client" });
    },

    // === Студенты ===
    getStudents: () => ApiService.request('/students', 'GET'),
    getStudent: (id) => ApiService.request(`/students/${id}`, 'GET'),
    createStudent: (studentData) => ApiService.request('/students', 'POST', studentData, false),
    updateStudent: (id, studentData) => ApiService.request(`/students/${id}`, 'PUT', studentData),
    deleteStudent: (id) => ApiService.request(`/students/${id}`, 'DELETE'),

    // === Курсы ===
    getCourses: () => ApiService.request('/courses', 'GET'),
    getCourse: (id) => ApiService.request(`/courses/${id}`, 'GET'),
    createCourse: (courseData) => ApiService.request('/courses', 'POST', courseData),
    updateCourse: (id, courseData) => ApiService.request(`/courses/${id}`, 'PUT', courseData),
    deleteCourse: (id) => ApiService.request(`/courses/${id}`, 'DELETE'),

    // === Записи на курсы (Enrollments) ===
    enrollStudent: (enrollmentData) => ApiService.request('/enrollments', 'POST', enrollmentData),
    getEnrollmentsForStudent: (studentId) => ApiService.request(`/students/${studentId}/enrollments`, 'GET'),
    getEnrollmentsForCourse: (courseId) => ApiService.request(`/courses/${courseId}/enrollments`, 'GET'),
    getEnrollmentById: (enrollmentId) => ApiService.request(`/enrollments/${enrollmentId}`, 'GET'),
    unenrollStudent: (enrollmentId) => ApiService.request(`/enrollments/${enrollmentId}`, 'DELETE'),
    getAllEnrollments: () => ApiService.request('/enrollments', 'GET'), // <<--- ДОБАВЛЕН для админа

    // === Оценки (Grades) ===
    getGradesForStudent: (studentId) => ApiService.request(`/students/${studentId}/grades`, 'GET'),
    getGradesForEnrollment: (enrollmentId) => ApiService.request(`/enrollments/${enrollmentId}/grades`, 'GET'),
    addGrade: (gradeData) => ApiService.request('/grades', 'POST', gradeData),
    getGradeById: (gradeId) => ApiService.request(`/grades/${gradeId}`, 'GET'),
    updateGrade: (gradeId, gradeData) => ApiService.request(`/grades/${gradeId}`, 'PUT', gradeData),
    deleteGrade: (gradeId) => ApiService.request(`/grades/${gradeId}`, 'DELETE'),

    // === Документы ===
    getDocumentsForStudent: (studentId) => ApiService.request(`/students/${studentId}/documents`, 'GET'),
    deleteDocument: (documentId) => ApiService.request(`/documents/${documentId}`, 'DELETE'),
    getDocumentMetadataById: (documentId) => ApiService.request(`/documents/${documentId}`, 'GET'),
    uploadDocument: async (studentId, formData) => {
        const url = `${clientConfig.serverUrl}/students/${studentId}/documents/upload`;
        const headers = {};
        const token = Auth.getToken();

        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        } else {
            console.warn(`Attempting to upload document for student ${studentId} without a token.`);
            Auth.logout();
            AppRouter.navigateTo('/login');
            throw new Error('Authentication required for document upload.');
        }

        try {
            const response = await fetch(url, {
                method: 'POST',
                headers: headers,
                body: formData,
            });

            const responseData = await response.json();
            if (!response.ok) {
                const errorMessage = (responseData && responseData.error) ? responseData.error : `Upload failed with status ${response.status}`;
                console.error(`API Error ${response.status}:`, errorMessage);
                if (response.status === 401) {
                    Auth.logout();
                    AppRouter.navigateTo('/login');
                }
                throw new Error(errorMessage);
            }
            return responseData;
        } catch (error) {
            console.error('Document Upload Error:', error.message, error);
            throw error;
        }
    }
};