@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
      "shared",
      "llm :: api",
      "document :: api",
      "court :: api",
      "recyclebin :: api"
    })
package com.pravoos.ai.core;
