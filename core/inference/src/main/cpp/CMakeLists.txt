cmake_minimum_required(VERSION 3.22.1)

project("nooz-llama" LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Nooz Flash's on-device digest/rewrite (owner's #6 — the "wiring error" the
# owner flagged) needs a real LLM runtime. Unlike Nooz Cast's onnxruntime,
# there is no prebuilt Maven AAR for llama.cpp, so this fetches the actual
# upstream source at a pinned commit and builds it as part of this module's
# native build — the same structure llama.cpp's own maintained Android sample
# (examples/llama.android in the upstream repo) uses: add_subdirectory the
# real llama.cpp CMake project, link against its `llama` and `llama-common`
# targets, and drive them from a small JNI bridge (nooz_llama_jni.cpp, adapted
# from that sample's own lib/src/main/cpp/ai_chat.cpp).
include(FetchContent)
FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    # Pinned to the exact commit nooz_llama_jni.cpp's C API calls were read
    # against (llama.cpp doesn't cut curated version tags — every commit on
    # master gets its own `master-<shortsha>` tag from their own CI, so a
    # commit pin is that project's normal, expected way to depend on it).
    GIT_TAG        360e1349f0009c5ad99d21e3c4546b707addc68a
    GIT_SHALLOW    TRUE
)

# Only what this JNI bridge actually calls: `llama` plus its `common` helper
# library (chat templates, sampler, tokenizer). Every other buildable target
# in the llama.cpp tree — its own CLI tools, server, tests, examples — is
# irrelevant to an Android library and, for several of them, not even
# cross-compilable out of the box; add_subdirectory-ing a non-top-level build
# already defaults all of LLAMA_BUILD_{TESTS,TOOLS,EXAMPLES,SERVER,APP} to
# OFF (see llama.cpp's own CMakeLists.txt: they key off LLAMA_STANDALONE,
# which is only ON when llama.cpp is the top-level project), so the explicit
# OFFs below are just documentation of that, not new behavior.
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
# No HTTPS model-downloading from C++ — :core:data's ModelCatalogueRepository
# already owns every download, in Kotlin — so OpenSSL, otherwise a real
# Android cross-compile dependency to satisfy, is dead weight here.
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
# GGML_NATIVE would pass -march=native, tuned for the build host's own CPU
# rather than the phone's — fatal correctness bug for a cross-compile, not
# just a missed optimization. GGML_LLAMAFILE (hand-written SIMD matmul
# kernels) and GGML_OPENMP are optional speed layers on top of ggml's own
# always-correct CPU backend; both add real NDK cross-compile risk for a gain
# that mostly matters for long chat sessions, not the short single-shot
# rewrite/digest calls Flash actually makes. Left off in favor of the plain
# backend that's known to just work.
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(llama_cpp)

add_library(nooz-llama SHARED
        nooz_llama_jni.cpp)

target_include_directories(nooz-llama PRIVATE
        ${llama_cpp_SOURCE_DIR}
        ${llama_cpp_SOURCE_DIR}/common
        ${llama_cpp_SOURCE_DIR}/include
        ${llama_cpp_SOURCE_DIR}/ggml/include)

target_link_libraries(nooz-llama
        llama
        llama-common
        android
        log)
