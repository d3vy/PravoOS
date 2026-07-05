@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "shared",
                "identity :: api",
                "identity :: model",
                "identity :: enums",
                "identity :: repository",
                "collaboration :: api"})
package com.pravoos.user.registration;
