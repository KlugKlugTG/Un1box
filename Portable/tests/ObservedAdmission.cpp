#include "lighttouch.h"
#include <cassert>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <string>
#include <sys/stat.h>
#include <utility>
#include <vector>

namespace fs = std::filesystem;
static void put(const fs::path &path, const std::string &bytes) {
    fs::create_directories(path.parent_path());
    std::ofstream file(path, std::ios::binary | std::ios::trunc);
    file.write(bytes.data(), bytes.size());
    assert(file.good());
}
static std::string digest(const fs::path &path) {
    lt_error error{sizeof(error), 1, 0, {}};
    char value[65]{};
    assert(lt_sha256_file(path.c_str(), value, &error) == LT_OK);
    return value;
}
struct Observation {
    enum Cancel { never, initial, hash_chunk, between_files, completed_bytes, after_hashing } cancel = never;
    lt_device **output = nullptr;
    std::vector<std::pair<uint64_t, uint64_t>> calls;
    static int notify(void *opaque, uint64_t done, uint64_t total) {
        auto &self = *static_cast<Observation *>(opaque);
        assert(self.output && *self.output == nullptr);
        assert(done <= total);
        if (!self.calls.empty()) {
            assert(done >= self.calls.back().first && total >= self.calls.back().second);
            assert(done - self.calls.back().first <= 65536);
            if (self.calls.back().second)
                assert(total == self.calls.back().second);
        }
        const bool same = !self.calls.empty() && done && done == self.calls.back().first;
        self.calls.emplace_back(done, total);
        return self.cancel == initial || (self.cancel == hash_chunk && done >= 65536) ||
               (self.cancel == between_files && same && done < total) ||
               (self.cancel == completed_bytes && total && done == total) ||
               (self.cancel == after_hashing && total && done == total && same);
    }
    void complete(uint64_t total) const {
        assert(!calls.empty() && calls.front() == std::make_pair(uint64_t(0), uint64_t(0)));
        assert(calls.back() == std::make_pair(total, total));
    }
};
static void word(std::string &bytes, size_t offset, uint32_t value) {
    for (unsigned i = 0; i < 4; i++)
        bytes[offset + i] = char(value >> (8 * i));
}
static void packed_contracts(const fs::path &root) {
    // Two records, a hole, and the largest valid record index. This fixture
    // follows the common importer's full-address-space ITNAND01 layout.
    std::string valid(24 + 4 * 524288 * 4 + 2 * 4160, '\0');
    valid.replace(0, 8, "ITNAND01");
    word(valid, 8, 4096);
    word(valid, 12, 64);
    word(valid, 16, 4);
    word(valid, 20, 524288);
    word(valid, 24, 1);
    word(valid, 32, 2);
    struct Case {
        std::string name, bytes, board = "n72ap", format = "ITNAND01", expected;
        uint64_t pages = 2;
        bool bad_hash = false, mixed_listing = false, missing_source = false;
    };
    std::vector<Case> cases{{"valid", valid}, {"bad-magic", valid}, {"short-header", valid.substr(0, 23)},
                            {"page-size", valid}, {"spare-size", valid}, {"zero-chips", valid},
                            {"too-many-chips", valid}, {"zero-pages", valid}, {"too-many-pages", valid},
                            {"overflow-dimensions", valid}, {"short-index", valid.substr(0, 35)},
                            {"short-record", valid.substr(0, valid.size() - 1)}, {"slot-outside-file", valid},
                            {"wrong-page-count", valid}, {"bad-checksum", valid}, {"n81-packed", valid},
                            {"unknown-format", valid}, {"ambiguous-listing", valid}, {"missing-source", valid},
                            {"duplicate-slot", valid}, {"unreferenced-record", valid},
                            {"empty-records", valid.substr(0, 24 + 4 * 524288 * 4)}};
    cases[1].bytes[0] = '!'; cases[1].expected = "magic";
    cases[2].expected = "header";
    word(cases[3].bytes, 8, 2048); cases[3].expected = "page geometry";
    word(cases[4].bytes, 12, 12); cases[4].expected = "page geometry";
    word(cases[5].bytes, 16, 0); cases[5].expected = "address geometry";
    word(cases[6].bytes, 16, 5); cases[6].expected = "address geometry";
    word(cases[7].bytes, 20, 0); cases[7].expected = "address geometry";
    word(cases[8].bytes, 20, 4096 * 128 + 1); cases[8].expected = "address geometry";
    word(cases[9].bytes, 16, UINT32_MAX); word(cases[9].bytes, 20, UINT32_MAX);
    cases[9].expected = "address geometry";
    cases[10].expected = "index";
    cases[11].expected = "record";
    word(cases[12].bytes, 24, 3); cases[12].expected = "out of bounds";
    cases[13].pages = 1; cases[13].expected = "page count";
    cases[14].bad_hash = true; cases[14].expected = "checksum mismatch";
    cases[15].board = "n81ap"; cases[15].expected = "only for n72";
    cases[16].format = "other"; cases[16].expected = "only for n72";
    cases[17].mixed_listing = true; cases[17].expected = "integrity record";
    cases[18].missing_source = true; cases[18].expected = "source_listing_sha256";
    word(cases[19].bytes, 32, 1); cases[19].expected = "duplicate";
    word(cases[20].bytes, 32, 0); cases[20].expected = "unreferenced";
    word(cases[21].bytes, 24, 0); word(cases[21].bytes, 32, 0); cases[21].pages = 0;
    for (const auto &fixture : cases) {
        const fs::path base = root / fixture.name / "base", state = base.parent_path() / "state";
        put(base / "nand", fixture.bytes);
        put(base / "nor.bin", "nor");
        put(base / "gid-blobs.bin", "gid");
        put(base / "iBoot.bin", "boot");
        put(base / "source-device.lock.json", "original lock retained by common importer");
        const std::string provenance(64, 'a');
        const auto nand_hash = fixture.bad_hash ? std::string(64, '0') : digest(base / "nand");
        const std::string lock =
            "{\"format\":1,\"board\":\"" + fixture.board + "\",\"build\":\"7E18\",\"outputs\":{"
            "\"nand\":{\"path\":\"nand\",\"format\":\"" + fixture.format + "\",\"sha256\":\"" + nand_hash +
            "\",\"pages\":" + std::to_string(fixture.pages) +
            (fixture.missing_source ? "" : ",\"source_listing_sha256\":\"" + provenance + "\"") +
            (fixture.mixed_listing ? ",\"listing_sha256\":\"" + provenance + "\"" : "") +
            "},\"nor\":{\"path\":\"nor.bin\",\"sha256\":\"" + digest(base / "nor.bin") +
            "\"},\"iboot\":{\"path\":\"iBoot.bin\",\"sha256\":\"" + digest(base / "iBoot.bin") +
            "\"},\"gid_blobs\":{\"path\":\"gid-blobs.bin\",\"sha256\":\"" + digest(base / "gid-blobs.bin") + "\"}}}";
        put(base / "device.lock.json", lock);
        for (const auto &entry : fs::directory_iterator(base))
            assert(chmod(entry.path().c_str(), 0400) == 0);
        assert(chmod(base.c_str(), 0500) == 0);
        const std::string base_path = base.string(), state_path = state.string();
        lt_device_config config{sizeof(config), 1, base_path.c_str(), state_path.c_str(), 0};
        lt_error error{sizeof(error), 1, 0, {}};
        lt_device *device = nullptr;
        Observation observation{Observation::never, &device, {}};
        const auto result = lt_device_open_observed(&config, Observation::notify, &observation, &device, &error);
        if (!fixture.expected.empty()) {
            if (result == LT_OK || !std::strstr(error.message, fixture.expected.c_str()))
                std::cerr << fixture.name << ": " << result << " " << error.message << "\n";
            assert(result != LT_OK && device == nullptr && std::strstr(error.message, fixture.expected.c_str()));
            assert(!fs::exists(state / ".base-identity") && !fs::exists(state / ".dirty"));
            continue;
        }
        assert(result == LT_OK && device);
        // Structural index checks do not inflate the asset hashing progress.
        observation.complete(fixture.bytes.size() + 3 + 3 + 4);
        const auto rom = root / "rom";
        put(rom, "synthetic ROM");
        const std::string rom_path = rom.string();
        lt_boot_config boot{sizeof(boot), 1, "iPod-Touch-2G", rom_path.c_str(), nullptr, nullptr, 0, 0};
        int argc = 0;
        const char *const *argv = nullptr;
        assert(lt_device_boot_argv(device, &boot, &argc, &argv, &error) == LT_OK);
        assert(argc >= 3 && std::strstr(argv[2], ("nand=" + base_path + "/nand").c_str()));
        assert(std::strstr(argv[2], ("nandrw=" + state_path + "/nand").c_str()));
        assert(fs::is_regular_file(base / "nand") && fs::is_directory(state / "nand"));
        lt_device *blocked = nullptr;
        assert(lt_device_open(&config, &blocked, &error) == LT_BUSY && !blocked);
        lt_device_close(device);
        device = nullptr;
        put(state / ".dirty", "retained");
        assert(lt_device_open(&config, &device, &error) == LT_DIRTY && !device);
        config.allow_dirty = 1;
        assert(lt_device_open(&config, &device, &error) == LT_OK && fs::exists(state / ".dirty"));
        lt_device_close(device);
        assert(digest(base / "nand") == nand_hash);
        std::ifstream pin(state / ".base-identity");
        std::string pinned;
        pin >> pinned;
        assert(pinned == digest(base / "device.lock.json"));
    }
}
int main() {
    char temporary[] = "/tmp/lighttouch-observed-open-XXXXXX";
    assert(mkdtemp(temporary));
    const fs::path root(temporary), base = root / "base";
    struct Cleanup {
        fs::path root;
        ~Cleanup() {
            for (const auto &entry : fs::recursive_directory_iterator(root))
                if (entry.is_directory())
                    chmod(entry.path().c_str(), 0700);
            fs::remove_all(root);
        }
    } cleanup{root};
    packed_contracts(root / "packed");
    const std::string page_a(131089, 'a'), page_z(65553, 'z'), nor = "nor", iboot = "boot", gid = "gid";
    const std::string identity = "{\"seed\":\"synthetic\"}";
    put(base / "nand/a.pages", page_a);
    put(base / "nand/z.pages", page_z);
    put(base / "nor.bin", nor);
    put(base / "iBoot.bin", iboot);
    put(base / "gid-blobs.bin", gid);
    put(base / "identity.json", identity);
    const std::string listing =
        "a.pages " + digest(base / "nand/a.pages") + "\nz.pages " + digest(base / "nand/z.pages") + "\n";
    put(root / "listing", listing);
    put(base / "device.lock.json",
        "{\"format\":1,\"board\":\"n72ap\",\"build\":\"7E18\",\"boot_strategy\":\"iboot\",\"outputs\":{"
        "\"nand\":{\"path\":\"nand\",\"listing_sha256\":\"" +
            digest(root / "listing") +
            "\",\"pages\":2},"
            "\"nor\":{\"path\":\"nor.bin\",\"sha256\":\"" +
            digest(base / "nor.bin") +
            "\"},"
            "\"iboot\":{\"path\":\"iBoot.bin\",\"sha256\":\"" +
            digest(base / "iBoot.bin") +
            "\"},"
            "\"gid_blobs\":{\"path\":\"gid-blobs.bin\",\"sha256\":\"" +
            digest(base / "gid-blobs.bin") +
            "\"}},"
            "\"identity\":{\"sha256\":\"" +
            digest(base / "identity.json") + "\"}}");
    for (const auto &entry : fs::recursive_directory_iterator(base))
        assert(chmod(entry.path().c_str(), entry.is_directory() ? 0500 : 0400) == 0);
    assert(chmod(base.c_str(), 0500) == 0);
    const uint64_t total = page_a.size() + page_z.size() + nor.size() + iboot.size() + gid.size() + identity.size();
    const std::string base_path = base.string();
    lt_error error{sizeof(error), 1, 0, {}};
    lt_device *device = nullptr;
    for (auto cancel : {Observation::initial, Observation::hash_chunk, Observation::between_files,
                        Observation::completed_bytes, Observation::after_hashing}) {
        const auto state = root / ("cancel-" + std::to_string(int(cancel)));
        const std::string state_path = state.string();
        lt_device_config config{sizeof(config), 1, base_path.c_str(), state_path.c_str(), 0};
        Observation observation{cancel, &device, {}};
        assert(lt_device_open_observed(&config, Observation::notify, &observation, &device, &error) == LT_CANCELLED);
        assert(device == nullptr && error.code == LT_CANCELLED && !fs::exists(state / ".dirty"));
        assert(!fs::exists(state / ".base-identity"));
        // Actual reopen succeeds after cancellation: the writer lease unwound.
        assert(lt_device_open_observed(&config, nullptr, nullptr, &device, &error) == LT_OK);
        lt_device_close(device);
        device = nullptr;
    }
    const auto state = root / "success";
    const std::string state_path = state.string();
    lt_device_config config{sizeof(config), 1, base_path.c_str(), state_path.c_str(), 0};
    Observation observation{Observation::never, &device, {}};
    assert(lt_device_open_observed(&config, Observation::notify, &observation, &device, &error) == LT_OK);
    observation.complete(total);
    lt_device_info observed{sizeof(observed), 1, {}, {}, {}, {}, 0}, plain{sizeof(plain), 1, {}, {}, {}, {}, 0};
    assert(lt_device_describe(device, &observed, &error) == LT_OK);
    lt_device *blocked = nullptr;
    assert(lt_device_open(&config, &blocked, &error) == LT_BUSY && blocked == nullptr);
    lt_device_close(device);
    device = nullptr;
    assert(lt_device_open(&config, &device, &error) == LT_OK);
    assert(lt_device_describe(device, &plain, &error) == LT_OK);
    assert(std::strcmp(observed.base_sha256, plain.base_sha256) == 0 && observed.dirty == plain.dirty);
    lt_device_close(device);
    device = nullptr;
    // A verified base is not read again while its files keep their identity.
    observation = {Observation::never, &device, {}};
    assert(lt_device_open_observed(&config, Observation::notify, &observation, &device, &error) == LT_OK);
    observation.complete(0);
    lt_device_close(device);
    device = nullptr;
    // Changing a file's metadata moves its change time: only that file is hashed again.
    assert(chmod((base / "nor.bin").c_str(), 0400) == 0);
    observation = {Observation::never, &device, {}};
    assert(lt_device_open_observed(&config, Observation::notify, &observation, &device, &error) == LT_OK);
    observation.complete(nor.size());
    lt_device_close(device);
    device = nullptr;
    fs::remove(state / ".verified-digests");
    put(state / ".dirty", "retained failure");
    config.allow_dirty = 1;
    observation = {Observation::hash_chunk, &device, {}};
    assert(lt_device_open_observed(&config, Observation::notify, &observation, &device, &error) == LT_CANCELLED);
    assert(device == nullptr && fs::exists(state / ".dirty"));
    config.allow_dirty = 0;
    assert(lt_device_open_observed(&config, nullptr, nullptr, &device, &error) == LT_DIRTY);

    // Record assets participate in the same fixed total, not a second epoch.
    const auto rom = root / "rom", package = root / "package";
    put(rom, std::string(70003, 'r'));
    put(package, std::string(70007, 'p'));
    const std::string library_path = (root / "library").string(), rom_path = rom.string(),
                      package_path = package.string();
    lt_library_config library_config{sizeof(library_config), 1, library_path.c_str()};
    lt_library *library = nullptr;
    assert(lt_library_open(&library_config, &library, &error) == LT_OK);
    lt_library_import_config imported{
        sizeof(imported),     1, base_path.c_str(), nullptr, "Synthetic", rom_path.c_str(),
        package_path.c_str(), 0, nullptr,           nullptr};
    lt_record_info record{sizeof(record), 1, {}, {}, {}, {}, {}, {}, {}, {}, {}, 0, 0};
    assert(lt_library_import(library, &imported, &record, &error) == LT_OK);
    observation = {Observation::hash_chunk, &device, {}};
    assert(lt_library_device_open_observed(library, record.id, 0, Observation::notify, &observation, &device, &error) ==
           LT_CANCELLED);
    assert(device == nullptr && !fs::exists(fs::path(record.state_path) / ".dirty"));
    observation = {Observation::never, &device, {}};
    assert(lt_library_device_open_observed(library, record.id, 0, Observation::notify, &observation, &device, &error) ==
           LT_OK);
    observation.complete(total + 70003 + 70007);
    lt_device_close(device);
    device = nullptr;
    assert(lt_library_device_open(library, record.id, 0, &device, &error) == LT_OK);
    lt_device_close(device);
    device = nullptr;
    assert(chmod(record.bootrom_path, 0600) == 0);
    auto bad_rom = std::string(70003, 'r');
    bad_rom.back() = '!';
    put(record.bootrom_path, bad_rom);
    assert(chmod(record.bootrom_path, 0400) == 0);
    observation = {Observation::never, &device, {}};
    assert(lt_library_device_open_observed(library, record.id, 0, Observation::notify, &observation, &device, &error) ==
           LT_INVALID);
    assert(device == nullptr && std::strstr(error.message, "ROM checksum mismatch"));
    // Only the rewritten ROM is read again.
    observation.complete(70003);
    lt_library_close(library);

    // Every byte may be reported without admission succeeding: a changed tail
    // still fails the full authoritative NAND listing, with no published handle.
    assert(chmod((base / "nand/z.pages").c_str(), 0600) == 0);
    auto damaged = page_z;
    damaged.back() = '!';
    put(base / "nand/z.pages", damaged);
    assert(chmod((base / "nand/z.pages").c_str(), 0400) == 0);
    observation = {Observation::never, &device, {}};
    assert(lt_device_open_observed(&config, Observation::notify, &observation, &device, &error) == LT_INVALID);
    assert(device == nullptr && fs::exists(state / ".dirty"));
    observation.complete(page_z.size());
    std::cout << "Observed admission/cancel/lease/dirty/integrity contracts PASS (synthetic; no engine)\n";
}
