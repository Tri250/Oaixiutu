#include <string>
#include <sstream>
#include <iomanip>
#include <random>
#include <chrono>

namespace alcedo {

std::string generate_hash_128() {
    std::random_device rd;
    std::mt19937_64 gen(rd());
    std::uniform_int_distribution<uint64_t> dis;

    uint64_t a = dis(gen);
    uint64_t b = dis(gen);

    std::stringstream ss;
    ss << std::hex << std::setfill('0')
       << std::setw(16) << a
       << std::setw(16) << b;
    return ss.str();
}

} // namespace alcedo
