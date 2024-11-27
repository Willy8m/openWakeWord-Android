cmake_minimum_required(VERSION 3.10)

# Define the project
project("com.example.wakeworddisplayimage")

# Include the headers directory
include_directories("${CMAKE_SOURCE_DIR}/headers")

# Add the ONNX Runtime shared library
set(ONNX_RUNTIME_LIB "${CMAKE_SOURCE_DIR}/jni/arm64-v8a/libonnxruntime.so")

# Include the shared library
add_library(onnxruntime SHARED IMPORTED)
set_target_properties(onnxruntime PROPERTIES IMPORTED_LOCATION ${ONNX_RUNTIME_LIB})

# Add your native source files
add_library(native-lib SHARED native-lib.cpp)

# Link the ONNX Runtime library to your native-lib
target_link_libraries(native-lib onnxruntime log)  # Include 'log' for Android logging
